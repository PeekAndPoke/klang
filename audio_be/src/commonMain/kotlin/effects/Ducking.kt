package io.peekandpoke.klang.audio_be.effects

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Sidechain ducking/compression processor.
 *
 * Reduces the volume of a signal based on the level of a sidechain (trigger) signal.
 * Commonly used for kick-bass ducking or pumping effects.
 *
 * @param sampleRate Audio sample rate in Hz
 * @param attackSeconds Time to return to normal volume after trigger stops (in seconds)
 * @param depth Amount of ducking (0.0 = no effect, 1.0 = full silence)
 */
class Ducking(
    private val sampleRate: Int,
    attackSeconds: Double = 0.1,
    depth: Double = 0.5,
) {
    /** Attack coefficient for envelope follower (how fast volume returns) */
    private var attackCoeff: Double = calculateCoefficient(attackSeconds)

    /** Current gain reduction (0.0 = full duck, 1.0 = no duck) */
    private var currentGain: Double = 1.0

    /** Ducking depth (0.0 = no ducking, 1.0 = full silence) */
    var depth: Double = depth.coerceIn(0.0, 1.0)
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** Attack time in seconds (return to normal) */
    var attackSeconds: Double = attackSeconds
        set(value) {
            field = value
            attackCoeff = calculateCoefficient(value)
        }

    /**
     * Processes audio with sidechain ducking.
     *
     * @param input The audio buffer to be ducked
     * @param sidechain The trigger signal (e.g., kick drum on another orbit)
     * @param blockSize Number of samples to process
     */
    fun process(input: DoubleArray, sidechain: DoubleArray, blockSize: Int) {
        require(input.size >= blockSize) { "Input buffer too small" }
        require(sidechain.size >= blockSize) { "Sidechain buffer too small" }

        for (i in 0 until blockSize) {
            // Calculate sidechain signal level (RMS-like envelope following)
            val sidechainLevel = abs(sidechain[i])

            // Calculate target gain based on sidechain level
            // When sidechain is loud, gain goes down (ducking)
            val targetGain = if (sidechainLevel > 0.01) {
                // Sidechain is active - reduce gain
                1.0 - (depth * min(1.0, sidechainLevel * 2.0))
            } else {
                // No sidechain signal - full volume
                1.0
            }

            // Smooth gain changes with envelope follower
            // Fast attack when ducking, slower release when returning
            currentGain = if (targetGain < currentGain) {
                // Fast attack (immediate ducking)
                targetGain
            } else {
                // Slower release (smooth return to normal)
                currentGain + attackCoeff * (targetGain - currentGain)
            }

            // Apply gain reduction
            input[i] *= currentGain
        }
    }

    /**
     * Calculate time constant coefficient for envelope follower.
     * Higher values = faster response.
     */
    private fun calculateCoefficient(timeSeconds: Double): Double {
        // Prevent division by zero and ensure minimum attack time
        val clampedTime = max(0.001, timeSeconds)
        // Time constant for 63% response
        return 1.0 - exp(-1.0 / (clampedTime * sampleRate))
    }

    /** Reset internal state */
    fun reset() {
        currentGain = 1.0
    }
}
