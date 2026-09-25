/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.adsrExpNorm
import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import kotlin.math.abs

/**
 * The opt-in de-click knob on the ignitor envelope `.adsr(...)`, `declickSeconds`, and the ONE
 * curvature every exponential stage bends at since phase 3 step 3c removed the `expK` knob
 * (maintainer, 2026-09-25): `ADSR_EXP_K`.
 *
 * The de-click defaults to today's behaviour (off), so the first test pins the default and the next
 * proves the knob reaches the audio. A constant DC = 1.0 upstream is used so the rendered output IS
 * the envelope gain per sample, isolating it from any oscillator motion.
 */
class AdsrIgnitorKnobsSpec : StringSpec({

    val sampleRate = 44100

    fun ctx(blockFrames: Int): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames,
        gateEndFrame = blockFrames,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    // Constant DC = 1.0 source: with it, `.adsr(...)` output equals the envelope gain per sample.
    val dc: Ignitor = object : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, c: IgniteContext) {
            val end = c.windowEnd
            for (i in c.offset until end) buffer[i] = 1.0
        }
    }

    fun render(sig: Ignitor, blockFrames: Int): AudioBuffer {
        val buf = AudioBuffer(blockFrames)
        sig.generate(buf, 440.0, ctx(blockFrames))
        return buf
    }

    "defaults are behaviour-identical: bare adsr() == explicit declick=0" {
        val n = 22050
        val bare = render(dc.adsr(0.05, 0.2, 0.4, 0.1), n)
        val explicit = render(dc.adsr(0.05, 0.2, 0.4, 0.1, declickSeconds = 0.0), n)
        for (i in 0 until n) explicit[i] shouldBe bare[i]
    }

    "declickSeconds>0 rounds the attack→decay corner (lower 2nd-difference at the join)" {
        val n = 22050
        val attack = 0.1
        val join = (attack * sampleRate).toInt() // 4410 — the attack→decay slope discontinuity

        // 2nd difference peaks at a slope corner; the de-click one-pole spreads it out.
        fun maxCornerNearJoin(buf: AudioBuffer): Double {
            var m = 0.0
            for (i in (join - 200) until (join + 200)) {
                val corner = abs(buf[i + 1] - 2.0 * buf[i] + buf[i - 1])
                if (corner > m) m = corner
            }
            return m
        }

        val raw = render(dc.adsr(attack, 0.2, 0.4, 0.1, declickSeconds = 0.0), n)
        val smoothed = render(dc.adsr(attack, 0.2, 0.4, 0.1, declickSeconds = 0.001), n)
        maxCornerNearJoin(smoothed) shouldBeLessThan maxCornerNearJoin(raw)
    }

    "every exponential stage bends at ADSR_EXP_K: the decay, sample for sample, against the closed form" {
        // The oracle is the curve's own formula at ADSR_EXP_K, written out here: an instant attack,
        // then a decay of N frames from 1 to 0 whose level at frame i is g(1 - i/N). The DSL path is
        // the one a song takes. Bit-exact, so a curvature other than ADSR_EXP_K anywhere on the way
        // (a knob that came back, a stale normaliser) is red.
        val n = 22050
        val decay = 0.4
        val decFrames = (decay * sampleRate).toInt()
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Constant(1.0),
            attackSec = IgnitorDsl.Constant(0.0),
            decaySec = IgnitorDsl.Constant(decay),
            sustainLevel = IgnitorDsl.Constant(0.0),
            releaseSec = IgnitorDsl.Constant(0.1),
        )
        val buf = render(dsl.toExciter(), n)

        fun law(k: Double, i: Int): Double = adsrExpShape(1.0 - i * (1.0 / decFrames), k, adsrExpNorm(k))

        for (i in 0 until decFrames) {
            buf[i].toRawBits() shouldBe law(ADSR_EXP_K, i).toRawBits()
        }

        // Anti-vacuous: another curvature is a DIFFERENT curve on these frames, so the row above
        // cannot pass on a law that ignores k.
        (0 until decFrames).any { buf[it] != law(6.0, it) } shouldBe true
    }

    "the old expK oscParam is an unread key: an override renders bit-identically to none" {
        // `oscp("expK", k)` was the one writer a user could still reach after the door went; the slot
        // is gone, so the value lands in the bag and nothing reads it.
        val n = 22050
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            attackSec = IgnitorDsl.Constant(0.0),
            decaySec = IgnitorDsl.Constant(0.4),
            sustainLevel = IgnitorDsl.Constant(0.0),
            releaseSec = IgnitorDsl.Constant(0.1),
        )
        val bufDefault = render(dsl.toExciter(), n)
        val bufOverride = render(dsl.toExciter(mapOf("expK" to 8.0)), n)
        for (i in 0 until n) bufOverride[i].toRawBits() shouldBe bufDefault[i].toRawBits()
    }

    // ── the slot bridge: oscParam overrides reach the new params (sprudel / custom-ignitor path) ──
    "oscParam override reaches the declickSeconds slot" {
        val n = 22050
        val dsl = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Sine(),
            attackSec = IgnitorDsl.Constant(0.1),
            decaySec = IgnitorDsl.Constant(0.2),
            sustainLevel = IgnitorDsl.Constant(0.4),
            releaseSec = IgnitorDsl.Constant(0.1),
        )
        val bufDefault = render(dsl.toExciter(), n)
        val bufOverride = render(dsl.toExciter(mapOf("declickSeconds" to 0.002)), n)
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }
})
