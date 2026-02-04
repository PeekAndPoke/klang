package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * A modulated Delay Line with linear interpolation and feedback control.
 *
 * **How it works:**
 * A delay line stores incoming audio samples in a circular buffer (ring buffer) and plays them back after
 * a specified duration.
 *
 * **Key Features:**
 * - **Fractional Delay:** Uses Linear Interpolation to read "between" samples. This allows for:
 *      1. Smooth delay time modulation without clicking artifacts ("zipper noise").
 *      2. Precise tuning for pitch-based effects (Flanger, Chorus, Karplus-Strong).
 * - **Short Delay Support:** Minimum delay is set to ~0.1ms, enabling short-delay effects like Flangers and Comb Filters.
 * - **Feedback Loop:** Feeds the delayed signal back into the input, creating repeating echoes.
 * - **Safety Limiter:** Hard clips the internal buffer to +/- 2.0 to prevent infinite volume explosions
 *   if feedback exceeds 100% (unstable system).
 *
 * **Optimizations:**
 * - **Block Processing:** The main loop is split into two chunks (before and after buffer wrap-around).
 *   This removes the expensive modulo/branch check (`if pos >= size`) from the inner sample loop,
 *   improving CPU efficiency significantly.
 */
class DelayLine(
    maxDelaySeconds: Double,
    val sampleRate: Int,
) {
    private val bufferSize = (maxDelaySeconds * sampleRate).toInt()
    private val buffer = StereoBuffer(bufferSize)
    private var writePos = 0

    // Current parameters
    var delayTimeSeconds: Double = 0.5
    var feedback: Double = 0.0

    // Safety saturation threshold to prevent explosion
    private val limit = 2.0

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        // Optimization: Split loop into two chunks to handle circular buffer wrapping
        // This removes the 'if (writePos >= bufferSize)' check from the inner loop
        val firstChunkLen = min(length, bufferSize - writePos)

        // Process first chunk (writePos -> buffer end)
        processInternal(buffer.left, input.left, output.left, 0, firstChunkLen, writePos)
        processInternal(buffer.right, input.right, output.right, 0, firstChunkLen, writePos)

        // Process second chunk (0 -> remaining) if we wrapped around
        if (firstChunkLen < length) {
            val secondChunkLen = length - firstChunkLen
            processInternal(buffer.left, input.left, output.left, firstChunkLen, secondChunkLen, 0)
            processInternal(buffer.right, input.right, output.right, firstChunkLen, secondChunkLen, 0)
        }

        writePos = (writePos + length) % bufferSize
    }

    private fun processInternal(
        buffer: DoubleArray,
        input: DoubleArray,
        output: DoubleArray,
        offset: Int,
        length: Int,
        startWritePos: Int,
    ) {
        var pos = startWritePos

        // Allow shorter delays (e.g. ~1ms) for flanging/chorus effects.
        // 10ms (0.01) was too restrictive.
        val minSamples = (0.0001 * sampleRate)

        // Use Double for delaySamples to support fractional delay
        val delaySamples = (delayTimeSeconds * sampleRate).coerceIn(minSamples, (bufferSize - 2).toDouble())

        // Integer part and fractional part for interpolation
        val delayInt = delaySamples.toInt()
        val alpha = delaySamples - delayInt // Fraction between 0.0 and 1.0

        for (i in 0 until length) {
            val inputIndex = offset + i

            // --- 1. Read from the past with Linear Interpolation ---

            // Read position for integer part
            var readIndex1 = pos - delayInt
            if (readIndex1 < 0) readIndex1 += bufferSize

            // Read position for next sample (for interpolation)
            var readIndex2 = readIndex1 - 1
            if (readIndex2 < 0) readIndex2 += bufferSize

            val s1 = buffer[readIndex1]
            val s2 = buffer[readIndex2]

            // Linear Interpolation: s1 + alpha * (s2 - s1)
            // This allows the delay time to exist "between" samples
            val delayedSignal = s1 + alpha * (s2 - s1)

            // --- 2. Feedback loop with Safety Clamping ---

            var newSample = input[inputIndex] + (delayedSignal * feedback)

            // Hard clip safety to prevent feedback explosions
            if (abs(newSample) > limit) {
                newSample = if (newSample > 0) limit else -limit
            }

            buffer[pos] = newSample

            // --- 3. Output ---
            output[inputIndex] += delayedSignal

            // Advance pointer (no wrap check needed here due to chunking)
            pos++
        }
    }
}
