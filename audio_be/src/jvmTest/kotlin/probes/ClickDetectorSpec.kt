/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The click detector's MEASURING functions ([measureClick], [clickHighpass], [firResponseDb], [worstClick]) on
 * synthetic signals whose answers are known by hand: a steady tone (nothing happens), a hard gain step at the tone's
 * peak, a 50 ms linear glide, a tone that starts from silence, the highpass's stopband and passband, and the fold of two
 * channels. The oracles are written from each signal's definition (the sine's own first and second differences in
 * closed form), never from the meter. Fast, and asserts nothing about an engine stage.
 */
class ClickDetectorSpec : StringSpec({

    val config = ClickConfig()
    val sr = config.sampleRate.toDouble()
    val e = 15000
    val frames = e + config.tailFrames + 128
    val w = 2.0 * PI * CLICK_TONE_HZ / sr
    val a = CLICK_TONE_LEVEL

    /** The tone at its peak at the event: a cos(w (n - e)). */
    fun x(n: Int): Double = a * cos(w * (n - e))

    /** The largest |first difference| of the steady tone: 2 a sin(w / 2) (the sampled peak lies within w / 2 of it). */
    val toneJump = 2.0 * a * sin(w / 2.0)

    /** The largest |second difference| of the steady tone: 4 a sin^2(w / 2). */
    val toneKink = 4.0 * a * sin(w / 2.0) * sin(w / 2.0)

    fun signal(gain: (Int) -> Double): DoubleArray = DoubleArray(frames) { n -> x(n) * gain(n) }

    "nothing happens: every ratio reads 1, the burst 0 dB, and the level does not move" {
        val r = measureClick(y = signal { 1.0 }, eventFrame = e, config = config)

        withClue("steady jump ratio") { r.jumpRatio shouldBe (1.0 plusOrMinus 1e-4) }
        withClue("steady kink ratio") { r.kinkRatio shouldBe (1.0 plusOrMinus 1e-4) }
        withClue("steady burst") { abs(r.burstRelDb) shouldBeLessThan 0.5 }
        withClue("steady level") { r.levelBefore shouldBe ((a / sqrt(2.0)) plusOrMinus 1e-3) }
        withClue("steady rise is none") { r.riseMs.isNaN() shouldBe true }
    }

    "a hard gain step at the tone's peak: the step against the tone's own motion, a burst, the level moved within one period" {
        val y = signal { n -> if (n < e) 1.0 else 0.25 }
        val r = measureClick(y = y, eventFrame = e, config = config)
        // The step's own differences, from the definition of y.
        val jumpAtStep = abs(0.25 * x(e) - x(e - 1))
        val kinkAtStep = max(abs(0.25 * x(e) - 2.0 * x(e - 1) + x(e - 2)), abs(0.25 * x(e + 1) - 2.0 * 0.25 * x(e) + x(e - 1)))

        withClue("step jump ratio") { r.jumpRatio shouldBe ((jumpAtStep / toneJump) plusOrMinus (1e-3 * jumpAtStep / toneJump)) }
        withClue("step kink ratio") { r.kinkRatio shouldBe ((kinkAtStep / toneKink) plusOrMinus (1e-3 * kinkAtStep / toneKink)) }
        withClue("step burst re the steady") { r.burstRelDb shouldBeGreaterThan 100.0 }
        withClue("step burst re the signal") { r.burstAbsDb shouldBeGreaterThan -30.0 }
        withClue("step level after") { r.levelAfter shouldBe ((0.25 * a / sqrt(2.0)) plusOrMinus 1e-3) }
        withClue("step rise") {
            r.riseMs shouldBeGreaterThan 3.0
            r.riseMs shouldBeLessThan config.levelFrames * 1000.0 / sr
        }
    }

    "a 50 ms linear glide: no jump, a kink only where the slope breaks, a rise of 40 ms" {
        val glide = 2400.0
        val gain = { n: Int -> if (n < e) 1.0 else 1.0 - 0.75 * minOf(1.0, (n - e + 1) / glide) }
        val y = signal(gain)
        val r = measureClick(y = y, eventFrame = e, config = config)
        // The largest second difference sits where the glide starts: the tone's own plus the gain's slope break there.
        val kinkAtStart = abs(gain(e) * x(e) - 2.0 * x(e - 1) + x(e - 2))

        withClue("glide jump ratio") { r.jumpRatio shouldBeLessThan 1.05 }
        withClue("glide kink ratio") { r.kinkRatio shouldBe ((kinkAtStart / toneKink) plusOrMinus (1e-3 * kinkAtStart / toneKink)) }
        withClue("glide rise") { r.riseMs shouldBe (40.0 plusOrMinus 1.0) }
        withClue("glide settle") { r.settleMs shouldBe (45.0 plusOrMinus 1.0) }
        withClue("glide burst re the signal") { r.burstAbsDb shouldBeLessThan -60.0 }
    }

    "a tone that starts from silence at a zero crossing: read against the note after it, a kink and no jump" {
        val y = DoubleArray(frames) { n -> if (n < e) 0.0 else a * sin(w * (n - e)) }
        val r = measureClick(y = y, eventFrame = e, config = config)
        // y[e] = 0 and y[e + 1] = a sin w: the first difference is the tone's own, the second a sin w against 4 a sin^2(w / 2).
        val kink = a * sin(w) / toneKink

        withClue("onset jump ratio") { r.jumpRatio shouldBe (1.0 plusOrMinus 1e-3) }
        withClue("onset kink ratio") { r.kinkRatio shouldBe (kink plusOrMinus (1e-3 * kink)) }
        withClue("onset level before") { r.levelBefore shouldBe 0.0 }
        withClue("onset rise") {
            r.riseMs shouldBeGreaterThan 3.0
            r.riseMs shouldBeLessThan config.levelFrames * 1000.0 / sr
        }
    }

    "the highpass: deep stopband at the tone and at 1 kHz, flat passband, half amplitude at its cutoff" {
        val taps = clickHighpass(config)

        withClue("highpass at 110 Hz") { firResponseDb(taps = taps, hz = 110.0) shouldBeLessThan -150.0 }
        withClue("highpass at 1 kHz") { firResponseDb(taps = taps, hz = 1000.0) shouldBeLessThan -150.0 }
        withClue("highpass at 10 kHz") { firResponseDb(taps = taps, hz = 10000.0) shouldBe (0.0 plusOrMinus 0.001) }
        withClue("highpass at its cutoff") { firResponseDb(taps = taps, hz = config.highpassHz) shouldBe (-6.02 plusOrMinus 0.1) }
    }

    "two channels fold into the worse one, field by field, a missing time taking the other's" {
        fun reading(ratio: Double, burst: Double, rise: Double, offset: Int) = ClickReading(
            jumpRatio = ratio, kinkRatio = ratio, jumpEvent = ratio, jumpSteady = 1.0, kinkEvent = ratio, kinkSteady = 1.0,
            kinkOffsetFrames = offset, burstRelDb = burst, burstAbsDb = burst, levelBefore = 0.1, levelAfter = 0.2, riseMs = rise, settleMs = rise,
        )

        val left = reading(ratio = 50.0, burst = -80.0, rise = Double.NaN, offset = 3)
        val right = reading(ratio = 2.0, burst = -20.0, rise = 45.0, offset = 9)
        val folded = worstClick(left, right)

        withClue("fold kink") { folded.kinkRatio shouldBe 50.0 }
        withClue("fold jump") { folded.jumpRatio shouldBe 50.0 }
        withClue("fold offset of the larger kink") { folded.kinkOffsetFrames shouldBe 3 }
        withClue("fold burst") { folded.burstAbsDb shouldBe -20.0 }
        withClue("fold rise") { folded.riseMs shouldBe 45.0 }
    }
})
