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
 * Uses linked stereo detection: the sidechain level is derived from max(abs(L), abs(R))
 * and a single shared envelope applies the same gain reduction to both channels.
 * This preserves the stereo image during ducking.
 *
 * @param sampleRate Audio sample rate in Hz
 * @param attackSeconds Recovery/release time — how fast the volume returns to normal after
 *   the sidechain trigger stops (in seconds). Note: the duck-down is instantaneous;
 *   this parameter only controls the return smoothing. Named "attack" for strudel compatibility.
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
     * Processes stereo audio with sidechain ducking (linked stereo detection).
     *
     * Detects sidechain level from max(abs(L), abs(R)) and applies the same gain
     * to both channels, preserving the stereo image.
     */
    fun processStereo(
        inputL: FloatArray,
        inputR: FloatArray,
        sidechainL: FloatArray,
        sidechainR: FloatArray,
        blockSize: Int,
    ) {
        for (i in 0 until blockSize) {
            // Linked stereo detection: use peak of both channels
            val sidechainLevel = max(abs(sidechainL[i].toDouble()), abs(sidechainR[i].toDouble()))

            val targetGain = if (sidechainLevel > 0.01) {
                1.0 - (depth * min(1.0, sidechainLevel * 2.0))
            } else {
                1.0
            }

            currentGain = if (targetGain < currentGain) {
                targetGain
            } else {
                currentGain + attackCoeff * (targetGain - currentGain)
            }

            inputL[i] = (inputL[i] * currentGain).toFloat()
            inputR[i] = (inputR[i] * currentGain).toFloat()
        }
    }

    /**
     * Processes mono audio with sidechain ducking.
     *
     * @param input The audio buffer to be ducked
     * @param sidechain The trigger signal (e.g., kick drum on another cylinder)
     * @param blockSize Number of samples to process
     */
    fun process(input: FloatArray, sidechain: FloatArray, blockSize: Int) {
        for (i in 0 until blockSize) {
            val sidechainLevel = abs(sidechain[i].toDouble())

            val targetGain = if (sidechainLevel > 0.01) {
                1.0 - (depth * min(1.0, sidechainLevel * 2.0))
            } else {
                1.0
            }

            currentGain = if (targetGain < currentGain) {
                targetGain
            } else {
                currentGain + attackCoeff * (targetGain - currentGain)
            }

            input[i] = (input[i] * currentGain).toFloat()
        }
    }

    /**
     * Calculate time constant coefficient for envelope follower.
     * Higher values = faster response.
     */
    private fun calculateCoefficient(timeSeconds: Double): Double {
        val clampedTime = max(0.001, timeSeconds)
        return 1.0 - exp(-1.0 / (clampedTime * sampleRate))
    }

    /** Reset internal state */
    fun reset() {
        currentGain = 1.0
    }
}
