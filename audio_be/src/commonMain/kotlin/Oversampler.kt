package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.Oversampler.Companion.TAPS
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * N-times oversampler for anti-aliased nonlinear processing.
 *
 * Upsamples by 2^[stages] using linear interpolation, applies a per-sample
 * transform at the oversampled rate, then decimates back via cascaded
 * 2x half-band FIR stages. Filter state persists across blocks for continuity.
 *
 * Buffer is borrowed from [ScratchBuffers.oversample] — no per-voice allocation.
 *
 * @param stages Number of 2x stages. 1 = 2x, 2 = 4x, 3 = 8x, etc.
 */
class Oversampler(val stages: Int) {

    val factor: Int = 1 shl stages

    private val decimators = Array(stages) { HalfBandState() }
    private var lastSample: Double = 0.0

    /**
     * Processes [length] samples from [buffer] starting at [offset].
     *
     * 1. Upsamples the region into a working buffer from [scratchBuffers]
     * 2. Applies [transform] to every sample at the oversampled rate
     * 3. Decimates back to original rate via cascaded half-band filters
     * 4. Writes results back into buffer[offset..offset+length)
     */
    fun process(
        buffer: AudioBuffer,
        offset: Int,
        length: Int,
        scratchBuffers: ScratchBuffers,
        transform: (AudioSample) -> AudioSample,
    ) {
        val oversampledLen = length * factor

        scratchBuffers.oversample(factor).use { work ->
            // Step 1: Upsample (linear interpolation, direct to target rate)
            upsample(buffer, offset, length, work)

            // Step 2: Apply nonlinear transform at oversampled rate
            for (i in 0 until oversampledLen) {
                work[i] = transform(work[i])
            }

            // Step 3: Cascaded 2x decimation (in-place in work buffer)
            var currentLen = oversampledLen
            for (stage in 0 until stages) {
                currentLen = decimate2x(decimators[stage], work, currentLen)
            }

            // Step 4: Copy back to original buffer
            work.copyInto(buffer, offset, 0, length)
        }
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

        lastSample = buffer[offset + length - 1]
    }

    // ── 2x decimation with half-band FIR ────────────────────────────────────────

    private fun decimate2x(state: HalfBandState, work: AudioBuffer, currentLen: Int): Int {
        val outLen = currentLen / 2
        var outIdx = 0
        for (i in 0 until currentLen) {
            state.push(work[i])
            // Output every other sample (odd-indexed after push)
            if (i and 1 == 1) {
                work[outIdx] = state.output()
                outIdx++
            }
        }
        return outLen
    }

    /**
     * Persistent state for one half-band decimation stage.
     * Circular delay buffer of [TAPS] doubles. Survives across process() calls
     * for filter continuity at block boundaries.
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
            // Center tap index: HALF_LEN samples behind current write position
            val centerIdx = (pos - HALF_LEN - 1 + TAPS) % TAPS
            var sum = CENTER_TAP * delay[centerIdx]

            // Non-zero symmetric pairs at odd offsets from center
            for (k in KERNEL.indices) {
                val off = 2 * k + 1
                val idxPlus = (centerIdx + off) % TAPS
                val idxMinus = (centerIdx - off + TAPS) % TAPS
                sum += KERNEL[k] * (delay[idxPlus] + delay[idxMinus])
            }

            return sum
        }
    }

    companion object {
        // 15-tap half-band FIR, ~60dB stopband rejection.
        // Non-zero taps (one side, symmetric). Offsets ±1, ±3, ±5, ±7 from center.
        // Half-band property: even-offset taps (except center) are zero.
        // Effective cost: 4 symmetric MACs + center = 5 multiplies per output sample.
        private val KERNEL = doubleArrayOf(
            0.33261825699561426,   // ±1
            -0.11553340575436945,  // ±3
            0.046063814906802995,  // ±5
            -0.013148666148047813, // ±7
        )
        private const val CENTER_TAP = 0.5
        private const val TAPS = 15
        private const val HALF_LEN = 7 // (TAPS - 1) / 2

        /**
         * Converts a user-facing oversampling factor to internal stages.
         * Non-power-of-2 values are floored to the previous power of 2.
         * Values <= 1 return 0 (no oversampling).
         */
        fun factorToStages(factor: Int): Int {
            if (factor <= 1) return 0
            var s = factor
            var stages = 0
            while (s > 1) {
                s = s shr 1
                stages++
            }
            return stages
        }
    }
}
