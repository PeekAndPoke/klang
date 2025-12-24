package io.peekandpoke.dsp

class DelayLine(
    maxDelaySeconds: Double,
    val sampleRate: Int,
) {
    private val bufferSize = (maxDelaySeconds * sampleRate).toInt()
    private val buffer = DoubleArray(bufferSize) // Java arrays init to 0.0 by default
    private var writePos = 0

    // Current parameters
    var delayTimeSeconds: Double = 0.5
    var feedback: Double = 0.0

    fun process(input: DoubleArray, output: DoubleArray, length: Int) {
        // Enforce a minimum delay of ~10ms (480 samples at 48k) to avoid 1-sample feedback explosions
        val minSamples = (0.01 * sampleRate).toInt()

        // Calculate delay in samples
        val delaySamples = (delayTimeSeconds * sampleRate).toInt().coerceIn(minSamples, bufferSize - 1)

        for (i in 0 until length) {
            // 1. Read from the past
            var readIndex = writePos - delaySamples
            // Handle wrap-around
            if (readIndex < 0) readIndex += bufferSize

            val delayedSignal = buffer[readIndex]

            // 2. Feedback loop: Input + (Delayed * Feedback)
            // Note: We clamp the internal value slightly to prevent potential infinite growth if feedback > 1.0
            val newSample = input[i] + (delayedSignal * feedback)
            buffer[writePos] = newSample

            // 3. Output mixing
            output[i] += delayedSignal

            // Advance pointer
            writePos++

            if (writePos >= bufferSize) writePos = 0
        }
    }
}
