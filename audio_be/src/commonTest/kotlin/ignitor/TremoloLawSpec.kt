/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.FAST_SIN_MAX_ERROR
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.parseLfoShape
import io.peekandpoke.klang.audio_be.wrapPhase
import kotlin.math.abs
import kotlin.math.sin

/**
 * THE tremolo law (`TremoloCore`), pinned on its host, the Ignitor `Tremolo` node, against values computed in this
 * file: ledger W2 (the phase wrap survives hostile rates) and ledger W10 (skew, phase and shape reach the LFO).
 * Until phase 3 step 9 these rows ran on the voice strip's `TremoloRenderer`, the law's second host; with it gone
 * this spec is the law's oracle (a parity spec between two hosts could not see a change inside the shared core).
 *
 * The shape rows run at sampleRate 1000 / rate 1 Hz, so one LFO cycle is exactly 1000 samples
 * and sample `i` sits at cycle position `(i + 1) / 1000`: the core advances before it
 * evaluates. With `depth = 1.0` on a buffer of 1.0 the output IS the LFO level, so the
 * expected values below are the waveform itself, computed independently of the code.
 */
class TremoloLawSpec : StringSpec({

    /** One LFO cycle = 1000 samples. */
    val lfoRate = 1000

    /** The tremolo node over [input], its knobs constant, its shape named through the catalogue. */
    fun tremoloOver(
        input: DoubleArray,
        rate: Double = 1.0,
        depth: Double = 1.0,
        skew: Double = 0.0,
        startPhase: Double = 0.0,
        shape: String? = null,
    ): Ignitor = ArrayIgnitor(input).tremolo(
        rate = ConstantIgnitor(rate), depth = ConstantIgnitor(depth), skew = ConstantIgnitor(skew),
        shape = parseLfoShape(shape), startPhase = startPhase,
    )

    /** [input] through the tremolo node, one window at [sampleRate]. */
    fun render(
        input: DoubleArray,
        rate: Double = 1.0,
        depth: Double = 1.0,
        skew: Double = 0.0,
        startPhase: Double = 0.0,
        shape: String? = null,
        sampleRate: Int = lfoRate,
    ): DoubleArray = renderNodeWindows(tremoloOver(input, rate, depth, skew, startPhase, shape), listOf(input.size), sampleRate)

    fun renderCycle(
        skew: Double = 0.0,
        startPhase: Double = 0.0,
        shape: String? = null,
    ): AudioBuffer = render(AudioBuffer(1000) { 1.0 }, skew = skew, startPhase = startPhase, shape = shape)

    fun meanLevel(skew: Double, shape: String?): Double = renderCycle(skew = skew, shape = shape).average()

    // ── W2: the phase wrap ────────────────────────────────────────────────────────────────

    "a non-finite rate no longer NaN-poisons the voice — wrapPhase scrubs the phase to 0" {
        // Old code: phase += NaN once -> sin(NaN) -> NaN output for the voice's life. New:
        // the phase reads 0 every sample -> a steady gain of 1 - depth/2 (a level change, not
        // silence, not NaN — the reading recorded in the ledger).
        val buffer = render(AudioBuffer(256) { 0.8 }, rate = Double.NaN, depth = 0.5, sampleRate = 44100)

        buffer.all { it == 0.8 * 0.75 } shouldBe true
    }

    "a plain rate modulates: the wrap change is inert in range" {
        // 8820 samples = 1.6 LFO cycles at 8 Hz, so the gain genuinely sweeps its full
        // [1 - depth, 1] range (review round 1: a fifth of a cycle proved nothing).
        val buffer = render(AudioBuffer(8820) { 0.8 }, rate = 8.0, depth = 0.33, sampleRate = 44100)

        (buffer.max() > 0.79) shouldBe true
        (buffer.min() < 0.8 * (1.0 - 0.33) + 0.01) shouldBe true
        buffer.all { it.isFinite() } shouldBe true
    }

    // ── W10: the neutral settings are the shipped tremolo, to the polynomial sine's bound ──

    "the default sine is the shipped renderer to the polynomial sine's bound — the DrunkenSailor guard" {
        // The reference is an INDEPENDENT transcription of the pre-W10 loop with the library
        // sine, not a call back into the code under test. DrunkenSailor is the only shipped
        // tremolo (tremolo(depth = 0.33, sync = 8, shape = "sine")) and must not move beyond
        // what the polynomial sine moves it (parts in 10^11 of the gain, FAST_SIN_MAX_ERROR).
        val n = 8820
        val input = AudioBuffer(n) { 0.8 }

        val expected = AudioBuffer(n) { 0.0 }
        var refPhase = 0.0
        val refInc = (8.0 * TWO_PI) / 44100
        for (i in 0 until n) {
            refPhase = (refPhase + refInc).wrapPhase(TWO_PI)
            val refNorm = (sin(refPhase) + 1.0) * 0.5
            expected[i] = input[i] * (1.0 - (0.33 * (1.0 - refNorm)))
        }

        // null, the canonical name, its alias and a shouty spelling all resolve to the same
        // waveform — and all four must hit the same fast path, not the round trip: identical to
        // each other bit for bit, and to the library-sine reference within the bound.
        val canonical = render(AudioBuffer(n) { 0.8 }, rate = 8.0, depth = 0.33, shape = null, sampleRate = 44100)

        (0 until n).all { abs(canonical[it] - expected[it]) < FAST_SIN_MAX_ERROR } shouldBe true

        for (name in listOf("sine", "SINE", "sin")) {
            val buffer = render(AudioBuffer(n) { 0.8 }, rate = 8.0, depth = 0.33, shape = name, sampleRate = 44100)

            (0 until n).all { buffer[it] == canonical[it] } shouldBe true
        }
    }

    // ── W10: phase ───────────────────────────────────────────────────────────────────────

    "the authored phase seeds the LFO, in cycles" {
        // A quarter-cycle seed puts sample 0 one increment past the sine's quarter point.
        // If the seed were read as RADIANS the level here would be 0.627 instead of ~1.0.
        val buffer = render(AudioBuffer(4) { 1.0 }, startPhase = 0.25)

        val seeded = 0.25 * TWO_PI + TWO_PI / lfoRate
        abs(buffer[0] - (sin(seeded) + 1.0) * 0.5) shouldBeLessThan FAST_SIN_MAX_ERROR
    }

    "a seed outside 0..1 folds into the cycle, and a non-finite one heals to 0" {
        val quarter = renderCycle(startPhase = 0.25)

        // 3.25 cycles is a quarter cycle — three full turns are not a different LFO.
        val overshoot = renderCycle(startPhase = 3.25)
        (0 until 1000).all { abs(overshoot[it] - quarter[it]) < 1e-9 } shouldBe true

        // NaN would otherwise be added into the accumulator forever.
        val unseeded = renderCycle(startPhase = 0.0)
        val poisoned = renderCycle(startPhase = Double.NaN)
        (0 until 1000).all { poisoned[it] == unseeded[it] } shouldBe true
    }

    // ── W10: shape ───────────────────────────────────────────────────────────────────────

    "each waveform has its own landmarks — a shape swap cannot hide" {
        // Independently computed levels at cycle positions 0.101, 0.601 and 0.851. All five
        // shapes differ at every point, so any substitution shows up. The third probe is not
        // decoration: it is the only one past 0.75, so without it the triangle's third arm is
        // untested and `2.0 * t - 1.6` (which drives the level NEGATIVE, i.e. gain > 1)
        // survives the whole spec.
        val rows = listOf(
            "sine" to doubleArrayOf(0.796428410080530, 0.203571589919459, 0.097346057144423),
            "triangle" to doubleArrayOf(0.702, 0.298, 0.202),
            "square" to doubleArrayOf(1.0, 0.0, 0.0),
            "sawtooth" to doubleArrayOf(0.101, 0.601, 0.851),
            "ramp" to doubleArrayOf(0.899, 0.399, 0.149),
        )

        for ((name, expected) in rows) {
            val out = renderCycle(shape = name)

            abs(out[100] - expected[0]) shouldBeLessThan 1e-9
            abs(out[600] - expected[1]) shouldBeLessThan 1e-9
            abs(out[850] - expected[2]) shouldBeLessThan 1e-9
        }
    }

    "every waveform stays inside [0, 1] at every skew" {
        // The level IS the gain here (depth 1.0), so a level below 0 is a phase-INVERTED
        // boost and a level above 1 is a gain above unity — neither is a tremolo. This is
        // the class the landmark probes can only sample.
        for (shape in listOf("sine", "triangle", "square", "sawtooth", "ramp")) {
            for (skew in listOf(-1.0, -0.6, 0.0, 0.6, 1.0)) {
                val out = renderCycle(skew = skew, shape = shape)

                out.all { it >= 0.0 && it <= 1.0 } shouldBe true
            }
        }
    }

    "the square LFO is two-valued and the ramps are monotone within the cycle" {
        renderCycle(shape = "square").all { it == 0.0 || it == 1.0 } shouldBe true

        val saw = renderCycle(shape = "sawtooth")
        (1 until 999).all { saw[it] > saw[it - 1] } shouldBe true

        val ramp = renderCycle(shape = "ramp")
        (1 until 999).all { ramp[it] < ramp[it - 1] } shouldBe true
    }

    "an unknown shape name falls back to sine, and the house aliases resolve" {
        val sine = renderCycle(shape = "sine")
        val unknown = renderCycle(shape = "rampup") // a name this engine deliberately lacks
        (0 until 1000).all { unknown[it] == sine[it] } shouldBe true

        val triangle = renderCycle(shape = "triangle")
        for (alias in listOf("tri", "TRI")) {
            val out = renderCycle(shape = alias)
            (0 until 1000).all { out[it] == triangle[it] } shouldBe true
        }

        val square = renderCycle(shape = "square")
        for (alias in listOf("sqr", "pulse")) {
            val out = renderCycle(shape = alias)
            (0 until 1000).all { out[it] == square[it] } shouldBe true
        }

        val saw = renderCycle(shape = "sawtooth")
        val sawAlias = renderCycle(shape = "saw")
        (0 until 1000).all { sawAlias[it] == saw[it] } shouldBe true
    }

    // ── W10: skew ────────────────────────────────────────────────────────────────────────

    "skew warps how much of the cycle the waveform's first half gets" {
        // Measured on the square, where the level IS the duty. Counting is independent of the
        // warp formula. The exact split lands one sample off a round number at the knife edge
        // (t == duty exactly), hence the +/- 2 window.
        fun highSamples(skew: Double) = renderCycle(skew = skew, shape = "square").count { it == 1.0 }

        (abs(highSamples(0.6) - 800) <= 2) shouldBe true
        (abs(highSamples(0.0) - 500) <= 2) shouldBe true
        (abs(highSamples(-0.6) - 200) <= 2) shouldBe true
    }

    "skew reaches the SINE too — the shape it is easiest to leave behind" {
        // Round-1 finding: dropping the `duty == LFO_SYMMETRIC_DUTY` conjunct from the
        // bit-identity fast path makes skew silently inert on the DEFAULT shape (and on every
        // unknown name, which falls back to it) while all the other rows stay green. The
        // expectations are computed independently: at duty 0.8 the sine's peak moves from
        // cycle position 0.25 to 0.5 * 0.8 = 0.4, i.e. sample index 399.
        val out = renderCycle(skew = 0.6, shape = "sine")

        abs(out[100] - 0.693154269377208) shouldBeLessThan 1e-9
        abs(out[600] - 0.852162267128247) shouldBeLessThan 1e-9
        abs(out.average() - 0.690986913463273) shouldBeLessThan 1e-9
        // Explicit scan: DoubleArray has no indexOf in the common stdlib (NaN/-0.0 equality).
        var peakAt = 0
        for (i in out.indices) {
            if (out[i] > out[peakAt]) {
                peakAt = i
            }
        }
        peakAt shouldBe 399
    }

    "skew means the same thing on every waveform" {
        // Parameter parity, and the reason SAWTOOTH's duty is flipped in lfoDutyOf: its first
        // half is its QUIET half, so without the flip +0.6 would read 0.35 here while the
        // other four read 0.65-0.80. Positive skew = the LFO sits higher, on all five.
        for (shape in listOf("sine", "triangle", "square", "sawtooth", "ramp")) {
            (meanLevel(0.6, shape) > 0.6) shouldBe true
            (meanLevel(-0.6, shape) < 0.4) shouldBe true
        }
    }

    "the duty guard holds the extremes just short of the degenerate ends" {
        // Pins the guard's POSITION, not merely "it is finite": at skew +1 the square is
        // high for 999 of 1000 samples (duty 0.999). Widening to 0.0015 reads 998, to 0.15
        // reads 850, and dropping the coerceIn entirely reads 1000 — the division it exists
        // to avoid. Shrinking it is deliberately NOT pinned (0.0001 also reads 999/0 at this
        // resolution): a tighter guard only moves closer to the singularity, which is the
        // raw-engine direction anyway.
        renderCycle(skew = 1.0, shape = "square").count { it == 1.0 } shouldBe 999
        renderCycle(skew = -1.0, shape = "square").count { it == 1.0 } shouldBe 0
    }

    "skew stays finite at its extremes and a non-finite skew heals to symmetric" {
        for (skew in listOf(1.0, -1.0)) {
            renderCycle(skew = skew, shape = "sine").all { it.isFinite() } shouldBe true
        }

        val symmetric = renderCycle(skew = 0.0)
        for (skew in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val healed = renderCycle(skew = skew)
            (0 until 1000).all { healed[it] == symmetric[it] } shouldBe true
        }
    }

    // ── I2: the block framing does not reach the waveform ────────────────────────────────

    "ragged block splits render the same samples as one contiguous block" {
        // The LFO is a clock: it must not restart, skip or double-advance at a window edge,
        // for ANY shape (the skewed ones exercise the warp branch too).
        for (shape in listOf(null, "triangle", "square", "sawtooth", "ramp")) {
            val input = AudioBuffer(1000) { (it % 17) * 0.05 + 0.1 }
            val whole = render(input, skew = 0.4, shape = shape)
            val split = renderNodeWindows(tremoloOver(input, skew = 0.4, shape = shape), listOf(128, 1, 371, 500), lfoRate)

            for (i in 0 until 1000) {
                split[i] shouldBe whole[i]
            }
        }
    }
})
