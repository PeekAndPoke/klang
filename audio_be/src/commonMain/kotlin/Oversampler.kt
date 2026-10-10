/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.utils.flushState
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * N-times oversampler for anti-aliased nonlinear processing: a cascade of 2x stages, each a POLYPHASE IIR HALF-BAND
 * (two parallel chains of first-order all-pass sections in z^2), up and down.
 *
 * **How it works:**
 * 1. [upsample] runs the cascade up, `2^stages` times the rate: per input sample each stage's two all-pass chains give
 *    the two output samples (the even and the odd phase of the interpolated stream).
 * 2. The caller shapes the oversampled block in place, in its own loop between the two halves.
 * 3. [decimate] runs the cascade down, highest stage first: per pair of input samples each stage sums its two chains
 *    (the later sample through the even chain, the earlier through the odd one) and halves.
 *
 * Filter state persists across round trips for inter-block continuity. The work buffer is the caller's lease from
 * [ScratchBuffers.oversample], held across both halves: no per-voice allocation. A voice's instance is fresh per note and
 * never reset; the one caller of [reset] is the Katalyst `distort` stage (through `DistortionCore.reset`).
 *
 * **The design** (2026-10-10, `docs/tasks/in-progress/iir-oversampler.md`; the maintainer: "we make the iir the
 * standard for now and add other methods later"): the elliptic half-band as two all-pass branches (Valenzuela and
 * Constantinides, IEE Proceedings, 1983; Krukowski and Kale, ISCAS 2001), the coefficients from the standard closed form
 * as in Laurent de Soras' HIIR library (WTFPL), computed offline and written below: [FIRST_STAGE] for the stage at
 * the base rate (8 coefficients, transition 0.04), [LATER_STAGE] for every stage above it (6 coefficients, transition
 * 0.08: its input is already band-limited, so only what would fold into the audible band must go).
 *
 * **Quality, measured at 48 kHz** (the bench `OversamplerBenchSpec`): the round trip is FLAT, an all-pass in level at
 * every audible frequency (0.00 dB from 20 Hz to 20 kHz), and the alias and image rejection is about 95 to 100 dB. The
 * FIR this replaced lost 2 dB at 16 kHz and rejected about 20 dB.
 *
 * **Latency, and its one catch**: the delay is lower than the FIR's but NOT the same at every frequency (an IIR's phase
 * is not linear). [groupDelaySamples] declares the low-frequency delay, exact from the coefficients: 3.07, 4.40, 5.06,
 * 5.39 input samples at 2x, 4x, 8x, 16x (the FIR: 4.0, 5.5, 6.25). Toward the top it rises (at 2x: 3.07 at 1 kHz, 3.33 at
 * 10 kHz, 3.89 at 16 kHz, 4.74 at 20 kHz), so a whole-sample pad on a dry path beside an oversampled one NOTCHES the top
 * of their sum (-28.5 dB at 18.25 kHz at 2x). A host that sums the two pads the dry path with a PHASE TWIN instead, an
 * unshaped round trip of the same oversampler (the `parallel` nodes and the Katalyst `distort` stage's dry path,
 * [unionOf]); the two then match at every frequency. A linear-phase kernel with a constant delay is a later type.
 *
 * **NaN and state**: an IIR keeps what it is fed. Every sample entering a chain is sterilised with [flushState] (NaN,
 * infinity and denormal to 0), so one bad sample can never silence a voice for good; the stored states are flushed of
 * denormals once per block.
 *
 * **`stages = 0`**: [upsample] and [decimate] are no-ops (zero work, no state change), for callers that may receive
 * `oversample = 1` from a DSL.
 *
 * @param stages Number of 2x stages. 1 = 2x, 2 = 4x, 3 = 8x. Negative values are coerced to 0 (no oversampling).
 */
class Oversampler(stages: Int) {

    val stages: Int = stages.coerceAtLeast(0)

    val factor: Int = 1 shl this.stages

    /** The up half per stage, stage 0 at the base rate. */
    private val ups = Array(this.stages) { HalfBand(chainsOf(it)) }

    /** The down half per stage, stage 0 at the base rate (run highest first). */
    private val downs = Array(this.stages) { HalfBand(chainsOf(it)) }

    /**
     * The FIRST HALF of a round trip: upsamples `source[offset, offset + length)` into [work] and returns the
     * oversampled count, `length * factor`. The caller shapes `work[0 until count]` in place, in its own loop, and
     * closes the round trip with [decimate]. [work] is the caller's lease from `ScratchBuffers.oversample(factor)`, held
     * across both halves:
     *
     * ```
     * scratchBuffers.oversample(os.factor).use { work ->
     *     val count = os.upsample(source = buffer, offset = offset, length = length, work = work)
     *
     *     for (i in 0 until count) {
     *         work[i] = shape(work[i]).nanGuard()
     *     }
     *
     *     os.decimate(work = work, target = buffer, offset = offset, length = length)
     * }
     * ```
     *
     * The loop between the halves is the caller's and runs inline, so no function value crosses the audio path (engine
     * tidy-up step 2, audit B4.1).
     *
     * In place, stage by stage: the first stage reads [source] and writes `work[0, 2 * length)`; every later stage first
     * moves its input to the upper half of the region it fills, `[len, 2 * len)`, and writes `[0, 2 * len)` front to
     * back, which never overwrites an input sample it has not read yet (output `2i + 1` < input `len + i + 1`).
     *
     * When [stages] is 0 this writes nothing and returns 0, and [decimate] writes nothing.
     */
    fun upsample(source: AudioBuffer, offset: Int, length: Int, work: AudioBuffer): Int {
        if (stages == 0) {
            return 0
        }

        ups[0].interpolate(source = source, from = offset, length = length, target = work)

        var len = 2 * length

        for (stage in 1 until stages) {
            for (i in 0 until len) {
                work[len + i] = work[i]
            }

            ups[stage].interpolate(source = work, from = len, length = len, target = work)
            len *= 2
        }

        return len
    }

    /**
     * The SECOND HALF of a round trip (see [upsample]): decimates `work[0 until length * factor)` back to the base rate,
     * the highest stage first, in place in [work], and writes the result into `target[offset, offset + length)`.
     * [offset] and [length] are the ones the [upsample] of this round trip took.
     */
    fun decimate(work: AudioBuffer, target: AudioBuffer, offset: Int, length: Int) {
        if (stages == 0) {
            return
        }

        var len = length * factor

        for (stage in stages - 1 downTo 0) {
            downs[stage].decimate(buffer = work, length = len)
            len /= 2
        }

        // Back into the caller's buffer, without `copyInto`'s typed-array view on JS (see `copyRangeInto`).
        work.copyRangeInto(destination = target, destinationOffset = offset, startIndex = 0, endIndex = length)
    }

    /**
     * Clears all filter state. A voice never calls it (its instance is fresh per note-on); the Katalyst `distort` stage
     * does, through `DistortionCore.reset`, when it enters Off or is cut hard: its cores live as long as their chain.
     */
    fun reset() {
        for (stage in 0 until stages) {
            ups[stage].reset()
            downs[stage].reset()
        }
    }

    /** A design's coefficients split into its two all-pass chains: the even-indexed ones, then the odd-indexed. */
    private class Chains(coefficients: DoubleArray) {
        val even = DoubleArray((coefficients.size + 1) / 2) { coefficients[2 * it] }
        val odd = DoubleArray(coefficients.size / 2) { coefficients[2 * it + 1] }
    }

    /**
     * One 2x stage of one direction: the two all-pass chains of a half-band, the even-indexed coefficients in one, the
     * odd-indexed in the other. Each section is `y = (x - y1) * a + x1`, an all-pass in z^2 run at the LOW rate of the
     * stage, unrolled for the two designs the stages use. Stateful, one per stage and direction; the states are flushed
     * of denormals once per block ([settle]).
     */
    private class HalfBand(chains: Chains) {
        private val even = chains.even
        private val odd = chains.odd
        private val evenX = DoubleArray(even.size)
        private val evenY = DoubleArray(even.size)
        private val oddX = DoubleArray(odd.size)
        private val oddY = DoubleArray(odd.size)

        /** True for the base-rate design (4 + 4 sections), false for the one above it (3 + 3): which unrolled loop runs. */
        private val wide = even.size == 4

        init {
            // An internal invariant, not user input: the loops below are written for exactly these two designs.
            require((even.size == 4 && odd.size == 4) || (even.size == 3 && odd.size == 3)) {
                "an oversampler stage has 8 or 6 coefficients, got ${even.size + odd.size}"
            }
        }

        /** Upsamples `source[from, from + length)` into `target[0, 2 * length)`: the even phase, then the odd. */
        fun interpolate(source: AudioBuffer, from: Int, length: Int, target: AudioBuffer) {
            if (wide) {
                interpolate44(source = source, from = from, length = length, target = target)
            } else {
                interpolate33(source = source, from = from, length = length, target = target)
            }
        }

        /** Decimates `buffer[0, length)` into `buffer[0, length / 2)`, in place (output `m` is written after `2m + 1` is read). */
        fun decimate(buffer: AudioBuffer, length: Int) {
            if (wide) {
                decimate44(buffer = buffer, length = length)
            } else {
                decimate33(buffer = buffer, length = length)
            }
        }

        // ── The two designs, unrolled ────────────────────────────────────────────────────────────────────────────────
        //
        // Each section is `y = (x - y1) * a + x1`, run sample by sample through the chain; pinned bit for bit against a
        // plain reference that loops over arrays (`OversamplerDecimatorParitySpec`). Every state and coefficient is a
        // local for the block, which the JIT keeps in registers instead of loading and storing arrays per sample:
        // measured 2026-10-10 on the JVM, a 128-frame round trip with a soft shaper went from 4.45 to 3.32 us at 2x and
        // from 8.54 to 5.03 us at 4x (the FIR it replaced: 2.97 and 3.77).

        /** [interpolate] for 4 + 4 sections (the base-rate stage). */
        private fun interpolate44(source: AudioBuffer, from: Int, length: Int, target: AudioBuffer) {
            val a0 = even[0]; val a1 = even[1]; val a2 = even[2]; val a3 = even[3]
            val b0 = odd[0]; val b1 = odd[1]; val b2 = odd[2]; val b3 = odd[3]
            var ex0 = evenX[0]; var ex1 = evenX[1]; var ex2 = evenX[2]; var ex3 = evenX[3]
            var ey0 = evenY[0]; var ey1 = evenY[1]; var ey2 = evenY[2]; var ey3 = evenY[3]
            var ox0 = oddX[0]; var ox1 = oddX[1]; var ox2 = oddX[2]; var ox3 = oddX[3]
            var oy0 = oddY[0]; var oy1 = oddY[1]; var oy2 = oddY[2]; var oy3 = oddY[3]

            for (i in 0 until length) {
                val x = source[from + i].flushState()

                var y = (x - ey0) * a0 + ex0; ex0 = x; ey0 = y
                var z = (y - ey1) * a1 + ex1; ex1 = y; ey1 = z
                y = (z - ey2) * a2 + ex2; ex2 = z; ey2 = y
                z = (y - ey3) * a3 + ex3; ex3 = y; ey3 = z
                target[2 * i] = z

                y = (x - oy0) * b0 + ox0; ox0 = x; oy0 = y
                z = (y - oy1) * b1 + ox1; ox1 = y; oy1 = z
                y = (z - oy2) * b2 + ox2; ox2 = z; oy2 = y
                z = (y - oy3) * b3 + ox3; ox3 = y; oy3 = z
                target[2 * i + 1] = z
            }

            evenX[0] = ex0; evenX[1] = ex1; evenX[2] = ex2; evenX[3] = ex3
            evenY[0] = ey0; evenY[1] = ey1; evenY[2] = ey2; evenY[3] = ey3
            oddX[0] = ox0; oddX[1] = ox1; oddX[2] = ox2; oddX[3] = ox3
            oddY[0] = oy0; oddY[1] = oy1; oddY[2] = oy2; oddY[3] = oy3

            settle()
        }

        /** [decimate] for 4 + 4 sections. */
        private fun decimate44(buffer: AudioBuffer, length: Int) {
            val a0 = even[0]; val a1 = even[1]; val a2 = even[2]; val a3 = even[3]
            val b0 = odd[0]; val b1 = odd[1]; val b2 = odd[2]; val b3 = odd[3]
            var ex0 = evenX[0]; var ex1 = evenX[1]; var ex2 = evenX[2]; var ex3 = evenX[3]
            var ey0 = evenY[0]; var ey1 = evenY[1]; var ey2 = evenY[2]; var ey3 = evenY[3]
            var ox0 = oddX[0]; var ox1 = oddX[1]; var ox2 = oddX[2]; var ox3 = oddX[3]
            var oy0 = oddY[0]; var oy1 = oddY[1]; var oy2 = oddY[2]; var oy3 = oddY[3]

            for (m in 0 until length / 2) {
                // NaN-guard on the signal entering the IIR, as in [interpolate].
                val later = buffer[2 * m + 1].flushState()
                val earlier = buffer[2 * m].flushState()

                var y = (later - ey0) * a0 + ex0; ex0 = later; ey0 = y
                var z = (y - ey1) * a1 + ex1; ex1 = y; ey1 = z
                y = (z - ey2) * a2 + ex2; ex2 = z; ey2 = y
                z = (y - ey3) * a3 + ex3; ex3 = y; ey3 = z
                val e = z

                y = (earlier - oy0) * b0 + ox0; ox0 = earlier; oy0 = y
                z = (y - oy1) * b1 + ox1; ox1 = y; oy1 = z
                y = (z - oy2) * b2 + ox2; ox2 = z; oy2 = y
                z = (y - oy3) * b3 + ox3; ox3 = y; oy3 = z

                buffer[m] = 0.5 * (e + z)
            }

            evenX[0] = ex0; evenX[1] = ex1; evenX[2] = ex2; evenX[3] = ex3
            evenY[0] = ey0; evenY[1] = ey1; evenY[2] = ey2; evenY[3] = ey3
            oddX[0] = ox0; oddX[1] = ox1; oddX[2] = ox2; oddX[3] = ox3
            oddY[0] = oy0; oddY[1] = oy1; oddY[2] = oy2; oddY[3] = oy3

            settle()
        }

        /** [interpolate] for 3 + 3 sections (every stage above the first). */
        private fun interpolate33(source: AudioBuffer, from: Int, length: Int, target: AudioBuffer) {
            val a0 = even[0]; val a1 = even[1]; val a2 = even[2]
            val b0 = odd[0]; val b1 = odd[1]; val b2 = odd[2]
            var ex0 = evenX[0]; var ex1 = evenX[1]; var ex2 = evenX[2]
            var ey0 = evenY[0]; var ey1 = evenY[1]; var ey2 = evenY[2]
            var ox0 = oddX[0]; var ox1 = oddX[1]; var ox2 = oddX[2]
            var oy0 = oddY[0]; var oy1 = oddY[1]; var oy2 = oddY[2]

            for (i in 0 until length) {
                // NaN-guard on the signal entering the IIR, as in [interpolate].
                val x = source[from + i].flushState()

                var y = (x - ey0) * a0 + ex0; ex0 = x; ey0 = y
                var z = (y - ey1) * a1 + ex1; ex1 = y; ey1 = z
                y = (z - ey2) * a2 + ex2; ex2 = z; ey2 = y
                target[2 * i] = y

                y = (x - oy0) * b0 + ox0; ox0 = x; oy0 = y
                z = (y - oy1) * b1 + ox1; ox1 = y; oy1 = z
                y = (z - oy2) * b2 + ox2; ox2 = z; oy2 = y
                target[2 * i + 1] = y
            }

            evenX[0] = ex0; evenX[1] = ex1; evenX[2] = ex2
            evenY[0] = ey0; evenY[1] = ey1; evenY[2] = ey2
            oddX[0] = ox0; oddX[1] = ox1; oddX[2] = ox2
            oddY[0] = oy0; oddY[1] = oy1; oddY[2] = oy2

            settle()
        }

        /** [decimate] for 3 + 3 sections. */
        private fun decimate33(buffer: AudioBuffer, length: Int) {
            val a0 = even[0]; val a1 = even[1]; val a2 = even[2]
            val b0 = odd[0]; val b1 = odd[1]; val b2 = odd[2]
            var ex0 = evenX[0]; var ex1 = evenX[1]; var ex2 = evenX[2]
            var ey0 = evenY[0]; var ey1 = evenY[1]; var ey2 = evenY[2]
            var ox0 = oddX[0]; var ox1 = oddX[1]; var ox2 = oddX[2]
            var oy0 = oddY[0]; var oy1 = oddY[1]; var oy2 = oddY[2]

            for (m in 0 until length / 2) {
                // NaN-guard on the signal entering the IIR, as in [interpolate].
                val later = buffer[2 * m + 1].flushState()
                val earlier = buffer[2 * m].flushState()

                var y = (later - ey0) * a0 + ex0; ex0 = later; ey0 = y
                var z = (y - ey1) * a1 + ex1; ex1 = y; ey1 = z
                y = (z - ey2) * a2 + ex2; ex2 = z; ey2 = y
                val e = y

                y = (earlier - oy0) * b0 + ox0; ox0 = earlier; oy0 = y
                z = (y - oy1) * b1 + ox1; ox1 = y; oy1 = z
                y = (z - oy2) * b2 + ox2; ox2 = z; oy2 = y

                buffer[m] = 0.5 * (e + y)
            }

            evenX[0] = ex0; evenX[1] = ex1; evenX[2] = ex2
            evenY[0] = ey0; evenY[1] = ey1; evenY[2] = ey2
            oddX[0] = ox0; oddX[1] = ox1; oddX[2] = ox2
            oddY[0] = oy0; oddY[1] = oy1; oddY[2] = oy2

            settle()
        }

        fun reset() {
            evenX.fill(0.0)
            evenY.fill(0.0)
            oddX.fill(0.0)
            oddY.fill(0.0)
        }

        /**
         * The stored states sterilised once per pass ([flushState]: a denormal to 0). Once per BLOCK, not per sample: the
         * input is already guarded per sample, so no NaN or infinity can reach a state, and a denormal costs time only,
         * never a wrong value, while it lives for less than a block. Per sample this check was most of the kernel's
         * cost (measured 2026-10-10 on the JVM).
         */
        private fun settle() {
            for (k in evenX.indices) {
                evenX[k] = evenX[k].flushState()
                evenY[k] = evenY[k].flushState()
            }

            for (k in oddX.indices) {
                oddX[k] = oddX[k].flushState()
                oddY[k] = oddY[k].flushState()
            }
        }
    }

    companion object {
        /**
         * The half-band of the stage at the base rate: 8 all-pass coefficients, transition 0.04 of its high rate (flat
         * to about 20 kHz at a 48 kHz base, rejection about 100 dB). Designed offline with the standard closed form (see
         * the class KDoc); the bench pins what they do, not the digits.
         */
        private val FIRST_STAGE = doubleArrayOf(
            0.04063346092419326, 0.1505051290226746, 0.3007570559918741, 0.4607745049614506,
            0.6095243148961883, 0.7385038411188573, 0.8492238103920661, 0.9497427837050002,
        )

        /** The half-band of every stage above the first: 6 coefficients, transition 0.08 (rejection about 95 dB where it matters). */
        private val LATER_STAGE = doubleArrayOf(
            0.04536216434896102, 0.16808748123450207, 0.33714968797907374,
            0.5223778543083537, 0.7080641363635384, 0.8974455911727738,
        )

        /** The coefficients of 2x stage [stage], 0 at the base rate. */
        internal fun coefficientsOf(stage: Int): DoubleArray = if (stage == 0) FIRST_STAGE else LATER_STAGE

        /** Each design split into its two chains once, shared by every instance (read only). */
        private val FIRST_CHAINS = Chains(FIRST_STAGE)
        private val LATER_CHAINS = Chains(LATER_STAGE)

        private fun chainsOf(stage: Int): Chains = if (stage == 0) FIRST_CHAINS else LATER_CHAINS

        /** How many all-pass sections one direction of 2x stage [stage] runs (for the census). */
        internal fun sectionsOf(stage: Int): Int = coefficientsOf(stage).size

        /**
         * The round trip's LOW-FREQUENCY group delay in INPUT samples for [stages] 2x stages, 0 without oversampling.
         * Exact from the coefficients: a first-order all-pass `y = (x - y1) a + x1`, run at a stage's low rate, delays DC
         * by `(1 - a) / (1 + a)` samples of that rate; a stage's round trip delays DC by the MEAN of its two chains' sums
         * (a half-band at DC is the average of its two branches), scaled to input samples by `2^-stage`. 3.07, 4.40,
         * 5.06, 5.39 at 2x to 16x; the measured centroid of the impulse response agrees to 1e-12
         * (`OversamplerGroupDelaySpec`). Toward the top the delay rises (see the class KDoc).
         */
        fun groupDelaySamples(stages: Int): Double {
            var delay = 0.0

            for (stage in 0 until stages) {
                val coefficients = coefficientsOf(stage)
                var sum = 0.0

                for (a in coefficients) {
                    sum += (1.0 - a) / (1.0 + a)
                }

                // the even chain's sum plus the odd chain's, halved: the mean of the two (each section counts 2(1-a)/(1+a)
                // at the stage's high rate, (1-a)/(1+a) at its low rate)
                delay += sum / (1 shl stage)
            }

            return delay
        }

        /**
         * How many input frames the round trip may still ring after its input stops, down to about 120 dB under the
         * input: its slowest pole decays by the largest coefficient of the base-rate stage per input frame, so
         * `ln(1e-6) / ln(max a)`: 268 frames with any oversampling, 0 without. At 2x the ring is under 1e-6 by frame 254;
         * at 4x and 8x the cascade meets that pole twice and stays above 1e-6 to about frame 300, under -114 dB past
         * 268 (audio review, round 2). A host that must not cut a tail asks this (the Katalyst `distort` stage's hold,
         * the `parallel` stage's twins).
         */
        fun tailFrames(stages: Int): Int {
            if (stages <= 0) {
                return 0
            }

            return ceil(ln(1e-6) / ln(FIRST_STAGE.max())).toInt()
        }

        /**
         * [groupDelaySamples] in whole frames, rounded (3, 4, 5 at 2x, 4x, 8x): the latency a bus stage reports
         * (`KatalystDistortEffect`). A voice keeps the exact delay and rounds only its pad (`BuiltIgnitor.latencySamples`).
         */
        fun latencyFrames(stages: Int): Int = groupDelaySamples(stages).roundToInt()

        /**
         * The oversamplers a `parallel` stage pads its branches to, each branch listing its own as stage counts: every
         * stage count as often as the branch that holds it most often, ascending (`[1]` and `[2, 2]` give `[1, 2, 2]`).
         * A branch that lacks some of them gets an unshaped round trip of each ([missingFrom]), a phase twin, so every
         * branch holds the same all-pass cascade (the class KDoc's dispersion).
         */
        fun unionOf(lists: List<List<Int>>): List<Int> =
            lists.flatten().distinct().sorted().flatMap { stages ->
                List(lists.maxOf { list -> list.count { it == stages } }) { stages }
            }

        /** What [union] holds that [have] does not, counted per stage count: `[1, 2, 2]` minus `[2]` is `[1, 2]`. */
        fun missingFrom(have: List<Int>, union: List<Int>): List<Int> {
            val left = have.toMutableList()

            return union.filter { stages -> !left.remove(stages) }
        }

        /**
         * Converts a user-facing oversampling factor to internal stages.
         *
         * - `factor <= 1` → 0 stages (no oversampling).
         * - Non-power-of-2 values are floored to the previous power of 2:
         *   `factor = 3` → stages 1 (effective factor 2),
         *   `factor = 7` → stages 2 (effective factor 4).
         */
        fun factorToStages(factor: Int): Int {
            if (factor <= 1) return 0
            return 31 - factor.countLeadingZeroBits() // floor(log2(factor))
        }

        /**
         * A factor that arrives as a knob value (the Ignitor `Shape` and `Distort` nodes' `oversample`,
         * read once at voice build since phase 3 step 3b, decision D7) as the whole factor
         * [factorToStages] takes: TRUNCATED toward zero, as the pattern door's `asIntOrNull` truncates
         * `distort(amount, shape, 2.9)` to 2, so both doors mean the same factor by the same number.
         *
         * A non-finite value is 0 (off): the wire's "unset", and `+Infinity` must not become the
         * largest Int. A huge finite value saturates to it, deliberately unclamped (the Motor stays
         * raw, and `ResourceWarehouse.WARM_OVERSAMPLE_FACTORS` records that "beyond it the trade is
         * the user's").
         */
        fun factorOf(knob: Double): Int {
            // NaN-guard on a value the author (or, from step 5, a pattern slot) can write.
            if (!knob.isFinite()) {
                return 0
            }

            return knob.toInt()
        }
    }
}
