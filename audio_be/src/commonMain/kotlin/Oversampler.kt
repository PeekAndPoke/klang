/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * N-times oversampler for anti-aliased nonlinear processing.
 *
 * **How it works:**
 * 1. Upsamples by `2^stages` using **linear interpolation** (direct-to-target).
 * 2. Caller-supplied `transform(sample)` is applied at the oversampled rate.
 * 3. Cascaded 2× decimation via a 15-tap half-band FIR (one stage per 2×), polyphase and
 *    indexed straight into the work buffer (see [decimate2x]).
 *
 * Filter state persists across `process()` calls for inter-block continuity.
 * Scratch buffer is borrowed from [ScratchBuffers.oversample] — no per-voice
 * allocation. [reset] exists for a future pooled-instance world; today every instance is
 * per-voice and nothing calls it (ledger W11).
 *
 * **Filter quality (honest characterisation):**
 * The half-band FIR has the canonical half-band null at fs/4 (|H(π/2)| = 0.5)
 * and unity DC gain, but is a truncated/windowed design, **not** an equiripple
 * 60 dB-stopband filter. Stopband attenuation is ~−14 dB at 0.55π, ~−20 dB at
 * 0.7π, deepening to −∞ at Nyquist. Combined with the linear-interpolation
 * upsampler (sinc² ≈ −26 dB image rejection), this is a **cheap-and-cheerful
 * anti-aliasing** stage, well-suited for clip/distort/crush waveshaping where
 * the nonlinearity dominates the spectrum anyway. It is **not** a transparent
 * resampler — don't expect spectral fidelity for clean signals.
 *
 * **Group delay** (in input samples):
 * - 2× (stages=1): ~4.0 samples (linear interp 0.5 + decimator FIR 3.5)
 * - 4× (stages=2): ~5.75 samples
 * - 8× (stages=3): ~6.625 samples
 *
 * **Sample-rate independence**: kernel coefficients are normalised; the
 * oversampler operates correctly at any input sample rate. Group delay is in
 * input samples, not seconds.
 *
 * **`stages = 0` semantics**: [process] is a no-op (zero work, no state
 * change) — used by callers that may receive `oversample = 1` from a DSL.
 *
 * @param stages Number of 2× stages. 1 = 2×, 2 = 4×, 3 = 8×. Negative values
 * are coerced to 0 (no oversampling).
 */
class Oversampler(stages: Int) {

    val stages: Int = stages.coerceAtLeast(0)

    val factor: Int = 1 shl this.stages

    private val decimators = Array(this.stages) { HalfBandState() }

    /**
     * The prefix view one decimation pass reads its first outputs from: the [HIST] samples before
     * the block, then the block's first [HEAD]. One per instance, the stages run one after the other.
     */
    private val prefix = DoubleArray(HIST + HEAD)
    private var lastSample: Double = 0.0

    /**
     * Processes [length] samples from [buffer] starting at [offset].
     *
     * 1. Upsamples the region into a working buffer from [scratchBuffers].
     * 2. Invokes [transformBlock] **once** with the work buffer and the
     *    oversampled-region count — the caller owns the per-sample loop and
     *    operates on `work[0 until count]` in place. This block-level
     *    callback avoids the per-sample `Function1.invoke` dispatch + Double
     *    boxing that a `(Double) -> Double` callback would force on JS.
     * 3. Decimates back to original rate via cascaded half-band filters.
     * 4. Writes results back into `buffer[offset..offset+length)`.
     *
     * **Caller contract — NaN-guard responsibility**: [transformBlock] MUST
     * sterilise NaN samples (e.g. with `.nanGuard()`) before they reach the
     * decimator FIR. A single NaN entering a stage poisons every output whose taps
     * reach it, up to 8 per stage (1 when it sits on an even, centre-only sample),
     * and it lives on in the stage's history into the next block.
     * Fusing the guard into the caller's per-sample expression gives a single
     * pass over the work buffer; a defensive second sweep here would force a
     * two-pass loop and measurably slow the path on V8.
     *
     * When [stages] is 0 this method is a no-op.
     */
    fun process(
        buffer: AudioBuffer,
        offset: Int,
        length: Int,
        scratchBuffers: ScratchBuffers,
        transformBlock: (work: AudioBuffer, count: Int) -> Unit,
    ) {
        if (stages == 0) return

        val oversampledLen = length * factor

        scratchBuffers.oversample(factor).use { work ->
            // Step 1: Upsample (linear interpolation, direct to target rate)
            upsample(buffer, offset, length, work)

            // Step 2: Apply caller's transform to the entire oversampled block in one call.
            // Caller is responsible for NaN-sterilising via .nanGuard() — see KDoc.
            transformBlock(work, oversampledLen)

            // Step 3: Cascaded 2x decimation (in-place in work buffer)
            var currentLen = oversampledLen
            for (stage in 0 until stages) {
                currentLen = decimate2x(decimators[stage], work, currentLen)
            }

            // Step 4: Copy back to original buffer
            work.copyInto(buffer, offset, 0, length)
        }
    }

    /**
     * Clears all internal filter state — every [HalfBandState] delay line and
     * the upsampler's `lastSample`. NO callers today, and that is fine: every
     * instance is per-voice (fresh per note-on on both the ignitor and strip
     * doors), so there is no reuse path and no stale tail to clear. Kept for
     * the planned warehouse-pool world, where pooled instances WILL need it
     * (ledger W11 — the old KDoc claimed a cleanup/retrigger lifecycle that
     * never existed).
     */
    fun reset() {
        for (d in decimators) {
            d.hist.fill(0.0)
        }
        lastSample = 0.0
    }

    // ── Linear interpolation upsample ───────────────────────────────────────────

    private fun upsample(buffer: AudioBuffer, offset: Int, length: Int, work: AudioBuffer) {
        val f = factor
        var prev = lastSample

        for (i in 0 until length) {
            val curr = buffer[offset + i]
            val base = i * f
            val step = (curr - prev) / f
            for (j in 0 until f) {
                work[base + j] = (prev + step * j)
            }
            prev = curr
        }

        lastSample = prev
    }

    // ── 2x decimation with half-band FIR ────────────────────────────────────────

    /**
     * One half-band pass, in place: `work[0 until currentLen]` in, `work[0 until currentLen / 2]`
     * out. Output `m` is the 15-tap FIR centred on stream sample `2m - 6`:
     *
     * ```
     * y[m] = 0.5 · s[2m-6] + k1 · (s[2m-5] + s[2m-7]) + k3 · (s[2m-3] + s[2m-9])
     *                      + k5 · (s[2m-1] + s[2m-11]) + k7 · (s[2m+1] + s[2m-13])
     * ```
     *
     * so it reads `s[2m-13 .. 2m+1]`, and the samples before the block come from the stage's
     * [HalfBandState.hist]. The taps index the buffer directly (polyphase: the centre is an even
     * sample, the four pairs are odd ones), nothing is pushed through a ring. Writing `y[m]` into
     * `work[m]` is safe once `2m - 13 >= m`, i.e. from output [PRE_OUT] on; the outputs before
     * that read the [prefix] view, which is filled before anything is overwritten.
     */
    private fun decimate2x(state: HalfBandState, work: AudioBuffer, currentLen: Int): Int {
        val n = currentLen

        if (n == 0) {
            return 0
        }

        // n is always even: length · factor, and factor is a power of two. An odd n would shift the
        // polyphase alignment of every later block, silently.
        val outLen = n ushr 1
        val hist = state.hist
        val pre = prefix
        val headLen = if (n < HEAD) n else HEAD

        // The prefix view: history, then the head of the block, both still untouched. Plain loops:
        // `copyInto` allocates a typed-array view per call on JS, and these move 13 to 26 doubles.
        for (i in 0 until HIST) {
            pre[i] = hist[i]
        }

        for (i in 0 until headLen) {
            pre[HIST + i] = work[i]
        }

        // The next pass's history, taken from the block before any output lands in it.
        if (n >= HIST) {
            val from = n - HIST

            for (i in 0 until HIST) {
                hist[i] = work[from + i]
            }
        } else {
            for (i in 0 until HIST - n) {
                hist[i] = hist[i + n]
            }

            for (i in 0 until n) {
                hist[HIST - n + i] = work[i]
            }
        }

        val preOut = if (outLen < PRE_OUT) outLen else PRE_OUT

        for (m in 0 until preOut) {
            work[m] = tap(pre, HIST + 2 * m)
        }

        for (m in preOut until outLen) {
            work[m] = tap(work, 2 * m)
        }

        return outLen
    }

    /** The FIR at `base = 2m` in [src]; the summation order is the one the ring version had. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun tap(src: DoubleArray, base: Int): Double {
        var sum = CENTER_TAP * src[base - 6]

        sum += K1 * (src[base - 5] + src[base - 7])
        sum += K3 * (src[base - 3] + src[base - 9])
        sum += K5 * (src[base - 1] + src[base - 11])
        sum += K7 * (src[base + 1] + src[base - 13])

        return sum
    }

    /**
     * Persistent state for one half-band decimation stage: the last [HIST] input samples of the
     * stream, oldest first. Survives across `process()` calls for filter continuity at block
     * boundaries.
     */
    private class HalfBandState {
        val hist = DoubleArray(HIST)
    }

    companion object {
        // 15-tap half-band FIR, the non-zero taps on one side (symmetric), at odd offsets ±1, ±3,
        // ±5, ±7 from the center tap. Half-band property: even-offset taps (except the center 0.5)
        // are zero, so `tap` costs 4 symmetric MACs + 1 center multiply per output sample. The
        // offsets in `tap`, HIST and PRE_OUT are all written for TAPS = 15: the unit moves together
        // or not at all. Quality is truncated half-band, not equiripple (see the class KDoc for
        // the honest stopband characterisation).
        private const val K1 = 0.33261825699561426
        private const val K3 = -0.11553340575436945
        private const val K5 = 0.046063814906802995
        private const val K7 = -0.013148666148047813

        /** Center tap (canonical half-band: 0.5). */
        private const val CENTER_TAP = 0.5

        /** Total FIR length. */
        private const val TAPS = 15

        /**
         * Stream samples before the block that a pass reads: output 0 reaches back to `s[-13]`,
         * the FIR's span minus the two samples of its own pair.
         */
        private const val HIST = TAPS - 2

        /**
         * The first output that can be written in place: output `m` reads down to `s[2m-13]`,
         * and it may overwrite `work[m]` only once nothing after it reads below `m`, which is
         * `2m - HIST >= m`.
         */
        private const val PRE_OUT = HIST

        /** Block samples the prefix view holds: the last prefix output reads up to `s[2·12+1]`. */
        private const val HEAD = 2 * PRE_OUT

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
