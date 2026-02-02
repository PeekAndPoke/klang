package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import kotlin.math.sin

/**
 * Tremolo effect - rhythmic amplitude modulation.
 * Uses a sine wave LFO to modulate the volume.
 *
 * @param rate LFO frequency in Hz (speed of the tremolo).
 * @param depth Modulation depth (0.0 = no effect, 1.0 = full modulation).
 * @param sampleRate Audio sample rate for calculating phase increment.
 */
class TremoloFilter(
    private val rate: Double,
    private val depth: Double,
    private val sampleRate: Int,
) : AudioFilter {

    // State: LFO phase position
    private var phase: Double = 0.0

    // Pre-calculate phase increment
    private val phaseIncrement: Double = (rate * TWO_PI) / sampleRate

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Early return if no tremolo
        if (depth <= 0.0) return

        for (i in 0 until length) {
            val idx = offset + i

            // Update phase
            phase += phaseIncrement
            if (phase > TWO_PI) phase -= TWO_PI

            // Generate sine LFO (-1.0 .. 1.0)
            val lfoRaw = sin(phase)

            // Map LFO to 0..1 range
            val lfoNorm = (lfoRaw + 1.0) * 0.5

            // Calculate gain: 1.0 (full volume) down to (1.0 - depth)
            // When lfoNorm = 1.0: gain = 1.0
            // When lfoNorm = 0.0: gain = 1.0 - depth
            val gain = 1.0 - (depth * (1.0 - lfoNorm))

            // Apply modulation
            buffer[idx] *= gain
        }
    }
}
