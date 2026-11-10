/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.common.math.semitones
import kotlin.math.abs
import kotlin.random.Random

/** The block an input moves at. */
private const val SWITCH_BLOCK: Int = 10

/** Blocks per render. */
private const val BLOCKS: Int = 30

/**
 * The superpluck's base delays follow their inputs at once (engine follow-up 4, 2026-10-19). The node keeps each
 * string's base delay and computes it again only when the note's frequency, the detune or the string count moves
 * (`SuperKarplusStrongIgnitor.baseDelays`, a cache that keeps per-string double calls off V8's per-block path). A
 * stale entry would keep a string at its old delay after an input moved.
 *
 * The oracle is the string itself. With brightness 1 (the loop filter passes the sample on), no stiffness, feedback
 * 1 and no drift, a string whose base delay is a whole number of samples `d` repeats itself: every output sample
 * equals the one `d` samples before, up to the rounding of the loop's one-pole. Each row moves one input at block
 * [SWITCH_BLOCK] to a setting whose delay is exactly 100 samples at 48 kHz (480 Hz, one string or no detune), and
 * checks that period on the blocks after the switch. A stale delay (120 samples, or a detuned 100.58) misses it by
 * the size of the signal. The loop delay (the loop filters' phase delay, taken out of the read, KLANG-00426) is part of
 * the same cache and has rows of its own at the end, the single pluck's cache among them. The -0.0 row has its own oracle: it compares the render against one that stays at 0.0, which
 * reads another delay.
 */
class SuperPluckBaseDelaySpec : StringSpec({
    val sampleRate = 48000
    val blockFrames = 128

    /** A control signal that reads [before] until [SWITCH_BLOCK] and [after] from there on (not block-constant). */
    class Switch(private val before: Double, private val after: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val v = if (ctx.voiceElapsedFrames / blockFrames < SWITCH_BLOCK) before else after

            for (i in ctx.offset until ctx.offset + ctx.length) {
                buffer[i] = v
            }
        }
    }

    fun render(node: Ignitor, freqHz: Double, blocks: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blocks * blockFrames,
            gateEndFrame = blocks * blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(11),
        )
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            node.generate(buf, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    /**
     * A superpluck that repeats itself at a whole-sample delay: brightness 1, no stiffness, feedback 1, no drift (the
     * loop filters then pass the sample on, so the loop delay is 0).
     */
    fun pluck(
        freq: Ignitor = FreqIgnitor,
        voices: Ignitor,
        detune: Ignitor,
        brightness: Ignitor = ConstantIgnitor(1.0),
        stiffness: Ignitor = ConstantIgnitor(0.0),
    ): Ignitor = Ignitors.superKarplusStrong(
        freq = freq,
        voices = voices,
        detune = detune,
        feedback = ConstantIgnitor(1.0),
        brightness = brightness,
        stiffness = stiffness,
        analog = ConstantIgnitor(0.0),
        rng = Random(7),
    )

    /** The single pluck node, under the same settings as [pluck]. */
    fun singlePluck(brightness: Ignitor, stiffness: Ignitor): Ignitor = Ignitors.karplusStrong(
        feedback = ConstantIgnitor(1.0),
        brightness = brightness,
        stiffness = stiffness,
        analog = ConstantIgnitor(0.0),
        rng = Random(7),
    )

    /** The largest `|y[n] - y[n - period]|` over the blocks after the switch's first [period] samples. */
    fun periodError(out: DoubleArray, period: Int): Double {
        var worst = 0.0

        for (n in SWITCH_BLOCK * blockFrames + period until out.size) {
            worst = maxOf(worst, abs(out[n] - out[n - period]))
        }

        return worst
    }

    /** The loudest sample after the switch: the period check is only worth something on a sounding string. */
    fun peakAfterSwitch(out: DoubleArray): Double {
        var peak = 0.0

        for (n in SWITCH_BLOCK * blockFrames until out.size) {
            peak = maxOf(peak, abs(out[n]))
        }

        return peak
    }

    fun shouldRepeatEvery100(out: DoubleArray, clue: String) {
        withClue("$clue: the string sounds after the switch") {
            peakAfterSwitch(out) shouldBeGreaterThan 0.05
        }

        withClue("$clue: every sample after the switch equals the one 100 samples before") {
            periodError(out, period = 100) shouldBeLessThan 1e-9
        }
    }

    "a moving frequency moves the base delay at once" {
        // 400 Hz (120 samples) for the first blocks, then 480 Hz (100 samples); one string, so no detune.
        val out = render(
            node = pluck(freq = Switch(before = 400.0, after = 480.0), voices = ConstantIgnitor(1.0), detune = ConstantIgnitor(0.2)),
            freqHz = 400.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(out, clue = "400 Hz to 480 Hz")
    }

    "a moving string count moves the base delays at once" {
        // Two detuned strings, then one: the string left plays its note undetuned, 100 samples at 480 Hz.
        val out = render(
            node = pluck(voices = Switch(before = 2.0, after = 1.0), detune = ConstantIgnitor(0.2)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(out, clue = "two strings to one")
    }

    "a moving detune moves the base delays at once" {
        // Two strings detuned by 0.4 semitones, then by 0: both at 100 samples.
        val out = render(
            node = pluck(voices = ConstantIgnitor(2.0), detune = Switch(before = 0.4, after = 0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(out, clue = "detune 0.4 to 0")
    }

    "a frequency of -0.0 after 0.0 reads its own delay" {
        // The two zeros are one number to `==`, but `sampleRate / f` is +Inf for one and -Inf for the other, which the
        // line clamps to its longest and its shortest delay: a render that switches from 0.0 to -0.0 must not sound
        // like one that stays at 0.0.
        val stays = render(
            node = pluck(freq = Switch(before = 0.0, after = 0.0), voices = ConstantIgnitor(1.0), detune = ConstantIgnitor(0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )
        val switches = render(
            node = pluck(freq = Switch(before = 0.0, after = -0.0), voices = ConstantIgnitor(1.0), detune = ConstantIgnitor(0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )
        var difference = 0.0

        for (n in SWITCH_BLOCK * blockFrames until stays.size) {
            difference = maxOf(difference, abs(stays[n] - switches[n]))
        }

        difference shouldBeGreaterThan 0.0
    }

    // The loop delay (KLANG-00426, L3-01) is cached with the base delays: brightness and stiffness move it. Before the
    // switch the loop filters delay the loop by about one sample (brightness 0.5) or half a sample (stiffness 0.3); at
    // brightness 1 without stiffness the loop delay is 0, so a stale one reads the line a sample or so short of 100.

    "a moving brightness moves the loop delays at once" {
        val out = render(
            node = pluck(voices = ConstantIgnitor(2.0), detune = ConstantIgnitor(0.0), brightness = Switch(before = 0.5, after = 1.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(out, clue = "brightness 0.5 to 1")
    }

    "a moving stiffness moves the loop delays at once" {
        val out = render(
            node = pluck(voices = ConstantIgnitor(2.0), detune = ConstantIgnitor(0.0), stiffness = Switch(before = 0.3, after = 0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(out, clue = "stiffness 0.3 to 0")
    }

    "the single pluck's loop delay follows its brightness and its stiffness at once" {
        val byBrightness = render(
            node = singlePluck(brightness = Switch(before = 0.5, after = 1.0), stiffness = ConstantIgnitor(0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )
        val byStiffness = render(
            node = singlePluck(brightness = ConstantIgnitor(1.0), stiffness = Switch(before = 0.3, after = 0.0)),
            freqHz = 480.0,
            blocks = BLOCKS,
        )

        shouldRepeatEvery100(byBrightness, clue = "pluck, brightness 0.5 to 1")
        shouldRepeatEvery100(byStiffness, clue = "pluck, stiffness 0.3 to 0")
    }

    // Each string is plucked over its read delay and renders with the loop delay of its OWN period and of the block's
    // feedback: the node against strings driven by hand from the same law (`KarplusString.baseDelayOf`, `loopDelayOf`),
    // bit for bit, with the feedback moving from 0.9 to 0.996 at [SWITCH_BLOCK]. A loop delay taken at another string's
    // period or kept from the old feedback, or a burst over the base delay, reads other samples.

    /**
     * Strings plucked and rendered by hand as the nodes do: each at its period in [periods] and its own loop delay, gain
     * [gain], brightness 0.2, stiffness 0.3, the feedback 0.9 before [SWITCH_BLOCK] and 0.996 from there on.
     */
    fun byHand(periods: List<Double>, gain: Double, blocks: Int): DoubleArray {
        val alpha = KarplusString.lpAlphaOf(0.2)
        val ap = KarplusString.apCoeffOf(0.3)
        val rng = Random(7)
        val strings = periods.map { KarplusString() }
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            val feedback = if (b < SWITCH_BLOCK) 0.9 else 0.996
            val loops = periods.map { KarplusString.loopDelayOf(period = it, lpAlpha = alpha, hasStiffness = true, apCoeff = ap, feedback = feedback) }

            for (n in periods.indices) {
                if (b == 0) {
                    strings[n].excite(lineDelay = periods[n] - loops[n], pickPos = 0.5, rng = rng)
                }

                strings[n].render(
                    buffer = buf, from = 0, to = blockFrames, baseDelay = periods[n], phaseMod = null,
                    hasDrift = false, driftStart = 1.0, driftStep = 0.0, loopDelay = loops[n],
                    lpAlpha = alpha, hasStiffness = true, apCoeff = ap, feedback = feedback, gain = gain, accumulate = n != 0,
                )
            }

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    "each superpluck string takes the loop delay of its own detuned period and of the feedback" {
        val node = Ignitors.superKarplusStrong(
            voices = ConstantIgnitor(2.0),
            detune = ConstantIgnitor(4.0),
            feedback = Switch(before = 0.9, after = 0.996),
            brightness = ConstantIgnitor(0.2),
            pickPosition = ConstantIgnitor(0.5),
            stiffness = ConstantIgnitor(0.3),
            analog = ConstantIgnitor(0.0),
            rng = Random(7),
        )
        val periods = (0 until 2).map { n ->
            KarplusString.baseDelayOf(sampleRate = sampleRate.toDouble(), freqHz = 1046.5 * Ignitors.getUnisonDetune(unison = 2, detune = 4.0, voiceIndex = n).semitones())
        }
        val out = render(node = node, freqHz = 1046.5, blocks = BLOCKS)
        val want = byHand(periods = periods, gain = 0.5, blocks = BLOCKS)

        for (i in out.indices) {
            withClue("superpluck against the strings by hand, sample $i") {
                out[i].toRawBits() shouldBe want[i].toRawBits()
            }
        }
    }

    "the single pluck takes the loop delay of its period and its feedback, and its burst spans the read delay" {
        val node = Ignitors.karplusStrong(
            feedback = Switch(before = 0.9, after = 0.996),
            brightness = ConstantIgnitor(0.2),
            pickPosition = ConstantIgnitor(0.5),
            stiffness = ConstantIgnitor(0.3),
            analog = ConstantIgnitor(0.0),
            rng = Random(7),
        )
        val out = render(node = node, freqHz = 1046.5, blocks = BLOCKS)
        val want = byHand(periods = listOf(KarplusString.baseDelayOf(sampleRate = sampleRate.toDouble(), freqHz = 1046.5)), gain = 1.0, blocks = BLOCKS)

        for (i in out.indices) {
            withClue("pluck against the string by hand, sample $i") {
                out[i].toRawBits() shouldBe want[i].toRawBits()
            }
        }
    }
})
