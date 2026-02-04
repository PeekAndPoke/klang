package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.ClippingFuncs.fastTanh
import io.peekandpoke.klang.audio_be.filters.AudioFilter

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

        // Local copy for loop performance (eliminates repeated field access)
        val d = drive

        for (i in 0 until length) {
            val idx = offset + i
            val x = buffer[idx] * d

            // Optimization: Fast tanh approximation
            // ~5x faster than kotlin.math.tanh with negligible error for audio
            buffer[idx] = fastTanh(x)
        }
    }
}
