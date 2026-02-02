package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import kotlin.math.tanh

/**
 * Distortion effect using hyperbolic tangent (soft clipping).
 * Adds harmonic saturation and warmth to the signal.
 *
 * @param amount Distortion amount (0.0 = clean, 1.0+ = heavily distorted).
 *               Drive factor scales from 1.0 to ~11.0 based on amount.
 */
class DistortionFilter(
    private val amount: Double,
) : AudioFilter {

    private val drive: Double = 1.0 + (amount * 10.0)

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Early return if no distortion
        if (amount <= 0.0) return

        for (i in 0 until length) {
            val idx = offset + i
            // Apply tanh saturation
            buffer[idx] = tanh(buffer[idx] * drive)
        }
    }
}
