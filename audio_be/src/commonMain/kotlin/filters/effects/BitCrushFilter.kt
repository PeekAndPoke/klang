package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import kotlin.math.floor
import kotlin.math.pow

/**
 * BitCrush effect - reduces bit depth for a lo-fi digital sound.
 *
 * @param amount Bit depth (0 = bypass, higher values = more crushing).
 *               Typical range: 1-8 bits of reduction.
 */
class BitCrushFilter(
    private val amount: Double,
) : AudioFilter {

    private val levels: Double = if (amount > 0.0) {
        2.0.pow(amount).toInt().toDouble()
    } else {
        0.0
    }

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Early return if no crushing
        if (amount <= 0.0 || levels <= 1.0) return

        val halfLevels = levels / 2.0

        for (i in 0 until length) {
            val idx = offset + i
            // Quantize the amplitude to discrete levels
            buffer[idx] = floor(buffer[idx] * halfLevels) / halfLevels
        }
    }
}
