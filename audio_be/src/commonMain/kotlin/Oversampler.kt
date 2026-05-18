package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.Oversampler.Companion.TAPS
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * N-times oversampler for anti-aliased nonlinear processing.
 *
 * **How it works:**
 * 1. Upsamples by `2^stages` using **linear interpolation** (direct-to-target).
 * 2. Caller-supplied `transform(sample)` is applied at the oversampled rate.
 * 3. Cascaded 2× decimation via a 15-tap half-band FIR (one stage per 2×).
 *
 * Filter state persists across `process()` calls for inter-block continuity.
 * Scratch buffer is borrowed from [ScratchBuffers.oversample] — no per-voice
 * allocation. Call [reset] to clear filter state (e.g. on voice retrigger).
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
     * 3. Sterilises any NaN samples in the work buffer to 0.0 — a single in-
     *    place sweep. Without this, a NaN from the transform would land in
     *    the decimator FIR delay line and poison every subsequent output
     *    until the NaN scrolls out (15+ samples per stage).
     * 4. Decimates back to original rate via cascaded half-band filters.
     * 5. Writes results back into `buffer[offset..offset+length)`.
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
            transformBlock(work, oversampledLen)

            // Step 3: NaN sterilisation — protects the decimator FIR delay lines.
            // Single sweep over the work buffer; still hot in L1 from step 2.
            for (i in 0 until oversampledLen) {
                work[i] = work[i].nanGuard()
            }

            // Step 4: Cascaded 2x decimation (in-place in work buffer)
            var currentLen = oversampledLen
            for (stage in 0 until stages) {
                currentLen = decimate2x(decimators[stage], work, currentLen)
            }

            // Step 5: Copy back to original buffer
            work.copyInto(buffer, offset, 0, length)
        }
    }

    /**
     * Clears all internal filter state — every [HalfBandState] delay line and
     * the upsampler's `lastSample`. Used by cylinder cleanup / voice
     * retrigger so a stale tail doesn't carry into a new note.
     */
    fun reset() {
        for (d in decimators) {
            d.delay.fill(0.0)
            d.pos = 0
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

    private fun decimate2x(state: HalfBandState, work: AudioBuffer, currentLen: Int): Int {
        // currentLen is always even (factor is a power of 2). Unrolled by 2:
        // push the even sample (no output), then push the odd sample and emit.
        val outLen = currentLen ushr 1
        var outIdx = 0
        var i = 0
        while (i < currentLen) {
            state.push(work[i])
            state.push(work[i + 1])
            work[outIdx] = state.output()
            outIdx++
            i += 2
        }
        return outLen
    }

    /**
     * Persistent state for one half-band decimation stage.
     * Circular delay buffer of [TAPS] doubles. Survives across `process()`
     * calls for filter continuity at block boundaries.
     */
    private class HalfBandState {
        val delay = DoubleArray(TAPS)
        var pos: Int = 0

        fun push(sample: Double) {
            delay[pos] = sample
            pos++
            if (pos >= TAPS) pos = 0
        }

        fun output(): Double {
            // The most recent push wrote to `delay[pos-1]` (and incremented
            // pos). Center of a 15-tap window ending there is `pos-1-7`,
            // i.e. 7 positions behind the most recent push.
            var centerIdx = pos - HALF_LEN - 1
            if (centerIdx < 0) centerIdx += TAPS

            var sum = CENTER_TAP * delay[centerIdx]

            // Non-zero symmetric pairs at odd offsets from center: ±1, ±3, ±5, ±7.
            // Branch-free wrap (single conditional add/subtract) — avoids `% TAPS`
            // since TAPS=15 isn't a power of two.
            var idxPlus = centerIdx + 1
            if (idxPlus >= TAPS) idxPlus -= TAPS
            var idxMinus = centerIdx - 1
            if (idxMinus < 0) idxMinus += TAPS

            for (k in KERNEL.indices) {
                sum += KERNEL[k] * (delay[idxPlus] + delay[idxMinus])
                idxPlus += 2
                if (idxPlus >= TAPS) idxPlus -= TAPS
                idxMinus -= 2
                if (idxMinus < 0) idxMinus += TAPS
            }

            return sum
        }
    }

    companion object {
        /**
         * 15-tap half-band FIR. Non-zero taps on one side (symmetric), at
         * odd offsets ±1, ±3, ±5, ±7 from the center tap. Half-band property:
         * even-offset taps (except the center 0.5) are zero, so the inner loop
         * costs 4 symmetric MACs + 1 center multiply per output sample.
         *
         * Quality is **truncated half-band, not equiripple** — see class KDoc
         * for the honest stopband characterisation.
         */
        private val KERNEL = doubleArrayOf(
            0.33261825699561426, // ±1
            -0.11553340575436945, // ±3
            0.046063814906802995, // ±5
            -0.013148666148047813, // ±7
        )

        /** Center tap (canonical half-band: 0.5). */
        private const val CENTER_TAP = 0.5

        /** Total FIR length. */
        private const val TAPS = 15

        /** (TAPS - 1) / 2 — number of samples on each side of center. */
        private const val HALF_LEN = 7

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
    }
}
