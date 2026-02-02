package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * Sample Rate Reducer effect (also known as "coarse" or downsampling).
 * Holds a sample value for multiple frames, creating a lo-fi digital sound.
 *
 * @param amount Downsampling factor (1.0 = no effect, higher = more reduction).
 *               Value of 2.0 means hold each sample for 2 frames (half sample rate).
 */
class SampleRateReducerFilter(
    private val amount: Double,
) : AudioFilter {

    // State: holds the last sampled value and frame counter
    private var lastValue: Double = 0.0
    private var counter: Double = 0.0

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Early return if no reduction
        if (amount <= 1.0) return

        for (i in 0 until length) {
            val idx = offset + i

            // Time to sample a new value?
            if (counter >= 1.0 || (i == 0 && counter == 0.0)) {
                lastValue = buffer[idx]
                counter -= 1.0
            }

            // Use the held value
            buffer[idx] = lastValue

            // Advance counter (inversely proportional to amount)
            counter += (1.0 / amount)
        }
    }
}
