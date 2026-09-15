/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.random.Random

/**
 * Raw-Motor abuse of the supersine: a frequency above the sample rate, or a `spread` typed in
 * cents into the semitone door, gives a per-sample phase increment of a whole cycle or more.
 * The stack's fast phase wrap is documented as unsafe for that, and the polynomial sine of a
 * phase that has escaped its fold diverges without bound (1.8e34 measured in review, the same
 * mirrored for a negative frequency). The voice must stay what the library sine made of it:
 * bounded, aliased noise, never non-finite.
 *
 * The trapezoids (the supersaw stack and the single-voice saw) share the wrap and cannot blow up
 * the way the polynomial does, but a lost wrap still shows: a positive increment parks every voice
 * on the low plateau (a full-scale DC), a negative one rides the rise ramp without bound.
 */
class SuperSineOutOfRangePhaseSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    // About one second: a drifting increment carries a lost wrap over the edge well inside it.
    val driftBlocks = 400

    fun render(sig: Ignitor, freqHz: Double, blocks: Int = 8, seed: Int = 7): DoubleArray {
        val c = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = sampleRate, gateEndFrame = sampleRate, releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames), random = Random(seed),
        )
        val out = DoubleArray(blocks * blockFrames)
        val buf = AudioBuffer(blockFrames)

        repeat(blocks) { b ->
            c.updateOffsetAndLength(0, blockFrames)
            sig.generate(buf, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            c.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun assertBounded(out: DoubleArray, clue: String) {
        var worst = 0.0

        for (v in out) {
            withClue("$clue: every sample finite") { v.isFinite() shouldBe true }
            worst = maxOf(worst, abs(v))
        }

        // The stack's gains are normalised to sum to exactly 1, so a stack of unit sines never
        // exceeds 1; the tolerance is rounding, not headroom.
        withClue("$clue: bounded like a sine stack") { worst shouldBeLessThan 1.0 + 1e-9 }
    }

    "a frequency above the sample rate (a whole cycle per sample) stays bounded, in either sign" {
        assertBounded(render(Ignitors.superSine(rng = Random(1)), freqHz = 2.0 * sampleRate), "freq = 2 x sampleRate")
        assertBounded(render(Ignitors.superSine(rng = Random(1)), freqHz = -2.0 * sampleRate), "freq = -2 x sampleRate")
    }

    "a spread of 200 semitones (a cents value in the semitone door) stays bounded, in either sign" {
        val sig = Ignitors.superSine(detune = ConstantIgnitor(200.0), rng = Random(1))

        assertBounded(render(sig, freqHz = 440.0), "spread = 200 st")
        assertBounded(render(Ignitors.superSine(detune = ConstantIgnitor(200.0), rng = Random(1)), freqHz = -440.0), "spread = 200 st, negative freq")
    }

    "a drifting voice just under one cycle per sample cannot be carried over the edge" {
        // analog drift multiplies the increment by a factor near 1. At analog = 20 its fast layer
        // (4 cents at three sigma, 50 ms time constant) swings the multiplier forty times the 1e-4
        // margin of dt = 0.9999, so a one-subtract wrap would lose the phase within the first few
        // hundred milliseconds of nearly every seed. Drift takes the safe wrap, so nothing escapes.
        for (seed in 1..2) {
            val sig = Ignitors.superSine(analog = ConstantIgnitor(20.0), rng = Random(seed))

            assertBounded(render(sig, freqHz = 0.9999 * sampleRate, blocks = driftBlocks), "dt = 0.9999 with drift, seed $seed")
        }
    }

    "an infinite frequency of either sign renders finite silence, not an infinity into the buses" {
        for (freq in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val out = render(Ignitors.superSine(rng = Random(1)), freqHz = freq)

            for (v in out) {
                withClue("freq = $freq") { v.isFinite() shouldBe true }
            }
        }
    }

    fun assertAliasing(out: DoubleArray, clue: String) {
        assertBounded(out, clue)

        // A parked trapezoid returns the same plateau value sample after sample, possibly only
        // for a while (a drifting increment can carry the phase back). A wrapping SAW never
        // repeats a sample exactly for long (setSawShape leaves both plateaus empty; a square
        // would sit on its high plateau legitimately): the detector is the longest run of equal
        // samples, saw shapes only.
        var run = 1
        var longest = 1

        for (i in 1 until out.size) {
            run = if (out[i] == out[i - 1]) run + 1 else 1
            longest = maxOf(longest, run)
        }

        withClue("$clue: not parked on a plateau (longest run of equal samples)") { longest shouldBeLessThan 16 }
    }

    // 2.37 cycles per sample, not 2: an increment of exactly two cycles wraps back onto the
    // same phase and a correctly wrapped voice would be a constant too.
    "the trapezoid stack under the same abuse aliases, in either sign" {
        assertAliasing(render(Ignitors.superSaw(rng = Random(1)), freqHz = 2.37 * sampleRate), "supersaw at 2.37 x sampleRate")
        assertAliasing(render(Ignitors.superSaw(rng = Random(1)), freqHz = -2.37 * sampleRate), "supersaw at -2.37 x sampleRate")
    }

    "a drifting trapezoid stack just under one cycle per sample cannot be carried over the edge" {
        // One parked voice hides behind six aliasing neighbours, so the stack is made to move as
        // one: no detune (every voice at dt = 0.9999) and no analog spread (one shared walk).
        for (seed in 1..2) {
            val sig = Ignitors.superSaw(
                detune = ConstantIgnitor(0.0), analog = ConstantIgnitor(20.0), analogSpread = ConstantIgnitor(0.0),
                rng = Random(seed),
            )

            assertAliasing(render(sig, freqHz = 0.9999 * sampleRate, blocks = driftBlocks), "drifting supersaw, seed $seed")
        }
    }

    "the single-voice saw under the same abuse aliases, in either sign" {
        assertAliasing(render(Ignitors.sawtooth(), freqHz = 2.37 * sampleRate), "saw at 2.37 x sampleRate")
        assertAliasing(render(Ignitors.sawtooth(), freqHz = -2.37 * sampleRate), "saw at -2.37 x sampleRate")
    }

    "a drifting single-voice saw just under one cycle per sample cannot be carried over the edge" {
        for (seed in 1..2) {
            val sig = Ignitors.sawtooth(analog = ConstantIgnitor(20.0))

            assertAliasing(render(sig, freqHz = 0.9999 * sampleRate, blocks = driftBlocks, seed = seed), "drifting saw, seed $seed")
        }
    }
})
