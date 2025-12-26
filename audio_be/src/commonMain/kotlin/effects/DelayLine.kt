package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer

class DelayLine(
    maxDelaySeconds: Double,
    val sampleRate: Int,
) {
    private val bufferSize = (maxDelaySeconds * sampleRate).toInt()
    private val buffer = StereoBuffer(bufferSize) // Java arrays init to 0.0 by default
    private var writePos = 0

    // Current parameters
    var delayTimeSeconds: Double = 0.5
    var feedback: Double = 0.0

    fun process(input: StereoBuffer, output: StereoBuffer, length: Int) {
        processInternal(buffer.left, input.left, output.left, length)
        processInternal(buffer.right, input.right, output.right, length)

        writePos = (writePos + length) % bufferSize
    }

    private fun processInternal(
        buffer: DoubleArray,
        input: DoubleArray,
        output: DoubleArray,
        length: Int,
    ) {
        var pos = writePos

        // Enforce a minimum delay of ~10ms (480 samples at 48k) to avoid 1-sample feedback explosions
        val minSamples = (0.01 * sampleRate).toInt()

        // Calculate delay in samples
        val delaySamples = (delayTimeSeconds * sampleRate).toInt().coerceIn(minSamples, bufferSize - 1)

        for (i in 0 until length) {
            // 1. Read from the past
            var readIndex = pos - delaySamples
            // Handle wrap-around
            if (readIndex < 0) readIndex += bufferSize

            val delayedSignal = buffer[readIndex]

            // 2. Feedback loop: Input + (Delayed * Feedback)
            // Note: We clamp the internal value slightly to prevent potential infinite growth if feedback > 1.0
            val newSample = input[i] + (delayedSignal * feedback)
            buffer[pos] = newSample

            // 3. Output mixing
            output[i] += delayedSignal

            // Advance pointer
            if (++pos >= bufferSize) pos = 0
        }
    }
}
