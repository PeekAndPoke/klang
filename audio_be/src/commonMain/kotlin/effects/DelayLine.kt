package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine.Companion.MIN_DELAY_SECONDS
import kotlin.math.abs
import kotlin.math.min

/**
 * A modulated stereo delay line with linear-interpolation fractional read and
 * feedback control.
 *
 * **How it works:**
 * Stores incoming audio samples in a circular ring buffer and plays them back
 * after a configurable duration. Stereo operation = two independent mono lines
 * (no cross-feedback / ping-pong).
 *
 * **Key features:**
 * - **Fractional read** via linear interpolation between adjacent ring-buffer
 *   samples. Enables smooth modulation of `delayTimeSeconds` (no zipper noise)
 *   and accurate tuning for pitch-based effects (flanger, chorus, comb filter,
 *   Karplus-Strong). Interpolation direction: `alpha=0` reads `s1` (newer
 *   sample, at `pos - delayInt`); `alpha=1` reads `s2` (one sample older).
 * - **Short-delay support** down to [MIN_DELAY_SECONDS] (~0.1 ms), enabling
 *   flanger/comb regimes. Note: linear interpolation introduces a mild HF
 *   roll-off (~−3 dB at Nyquist) that's only audible in short-delay use cases.
 * - **Feedback** path with smooth saturation safety: rather than a hard clip,
 *   the recirculated sample is passed through [ClippingFuncs.softCap] to
 *   prevent runaway accumulation when `feedback ≥ 1.0` while keeping the
 *   character musical (smooth tanh-style knee). NaN/Inf inputs are scrubbed
 *   to 0 before clamp so they cannot poison the ring buffer.
 *
 * **Output semantic (caller contract):** [process] writes the **wet (delayed)
 * signal additively** into `output`. The dry signal is NOT mixed in by this
 * class. Callers using DelayLine as a send/return effect should clear or
 * pre-fill `output` before calling. See `KatalystDelayEffect` for the canonical
 * usage pattern.
 *
 * **Performance:**
 * - Block processing: the inner sample loop has no ring-buffer wrap check.
 *   `process()` splits the block at the wrap boundary and calls the inner
 *   loop with two contiguous ranges instead.
 * - Per-block constants (`delayInt`, `alpha`, etc.) are computed once per
 *   `process()`, not per channel/chunk.
 */
class DelayLine(
    maxDelaySeconds: Double,
    val sampleRate: Int,
    delayTimeSeconds: Double = 0.5,
    feedback: Double = 0.0,
) {
    private val bufferSize = (maxDelaySeconds * sampleRate).toInt()
    private val buffer = StereoBuffer(bufferSize)
    private var writePos = 0

    /** Delay time in seconds. Setter silently ignores non-finite values. */
    var delayTimeSeconds: Double = delayTimeSeconds
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Feedback amount. Setter silently ignores non-finite values. Values ≥ 1.0
     *  are unstable but bounded by [ClippingFuncs.softCap] in the feedback path. */
    var feedback: Double = feedback
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /**
     * Returns true if the internal ring buffer still contains audio above
     * [threshold]. Used by cylinder-cleanup logic to detect tails that should
     * keep the cylinder alive.
     *
     * Cost: O(bufferSize × 2) — scans both channels linearly. Not intended for
     * per-block use; called from cleanup polling.
     *
     * Conservative under stable feedback (≤ 1.0): if every sample in the buffer
     * is below threshold, no future feedback iteration can bring the output
     * back above threshold, so a `false` return is safe.
     */
    fun hasTail(threshold: Double = 0.00001): Boolean {
        for (i in 0 until bufferSize) {
            if (abs(buffer.left[i]) > threshold || abs(buffer.right[i]) > threshold) {
                return true
            }
        }
        return false
    }

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        // Per-block constants — channel- and chunk-independent. Hoisted out of
        // the inner loop so they're computed once per process() call rather
        // than once per channel × chunk (4×).
        val delaySamples = (delayTimeSeconds * sampleRate)
            .coerceIn(MIN_DELAY_SECONDS * sampleRate, bufferSize - 2.0)
        val delayInt = delaySamples.toInt()
        val alpha = delaySamples - delayInt
        val fb = feedback

        // Split loop at the ring-buffer wrap boundary so the inner loop has no
        // 'if (pos >= bufferSize)' check.
        val firstChunkLen = min(length, bufferSize - writePos)

        processInternal(buffer.left, input.left, output.left, 0, firstChunkLen, writePos, delayInt, alpha, fb)
        processInternal(buffer.right, input.right, output.right, 0, firstChunkLen, writePos, delayInt, alpha, fb)

        if (firstChunkLen < length) {
            val secondChunkLen = length - firstChunkLen
            processInternal(buffer.left, input.left, output.left, firstChunkLen, secondChunkLen, 0, delayInt, alpha, fb)
            processInternal(buffer.right, input.right, output.right, firstChunkLen, secondChunkLen, 0, delayInt, alpha, fb)
        }

        writePos = (writePos + length) % bufferSize
    }

    private fun processInternal(
        buffer: AudioBuffer,
        input: AudioBuffer,
        output: AudioBuffer,
        offset: Int,
        length: Int,
        startWritePos: Int,
        delayInt: Int,
        alpha: Double,
        fb: Double,
    ) {
        var pos = startWritePos

        for (i in 0 until length) {
            val inputIndex = offset + i

            // --- 1. Fractional read: linear interpolation between two ring
            //         positions. `s1` is newer (at pos - delayInt), `s2` is one
            //         sample older. alpha=0 → s1, alpha=1 → s2.
            var readIndex1 = pos - delayInt
            if (readIndex1 < 0) {
                readIndex1 += bufferSize
            }
            var readIndex2 = readIndex1 - 1
            if (readIndex2 < 0) {
                readIndex2 += bufferSize
            }

            val s1 = buffer[readIndex1]
            val s2 = buffer[readIndex2]
            val delayedSignal = s1 + alpha * (s2 - s1)

            // --- 2. Feedback + safety. Scrub NaN/Inf first so it cannot poison
            //         the ring buffer (softCap of NaN is NaN — IEEE-754).
            var newSample = input[inputIndex] + (delayedSignal * fb)
            if (!newSample.isFinite()) {
                newSample = 0.0
            }
            // Smooth saturation around ±1 instead of a hard clip — softer,
            // less brittle character on runaway feedback or hot inputs.
            buffer[pos] = ClippingFuncs.softCap(newSample)

            // --- 3. Wet output, additive. Caller owns the dry mix.
            output[inputIndex] = output[inputIndex] + delayedSignal

            pos++
        }
    }

    companion object {
        /**
         * Lower bound for [delayTimeSeconds] in seconds. ~0.1 ms — short enough
         * for flanger/comb regimes, long enough that linear interpolation
         * doesn't blow up at boundary conditions.
         */
        private const val MIN_DELAY_SECONDS: Double = 0.0001
    }
}
