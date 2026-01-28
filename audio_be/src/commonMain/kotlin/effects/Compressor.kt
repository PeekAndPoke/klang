package io.peekandpoke.klang.audio_be.effects

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Dynamic range compressor with configurable threshold, ratio, knee, attack, and release.
 *
 * This compressor reduces the dynamic range of audio by attenuating signals above the threshold.
 * The amount of attenuation is controlled by the ratio parameter.
 *
 * @param sampleRate Sample rate in Hz
 * @param thresholdDb Threshold in dB (default: -20.0). Signals above this are compressed.
 * @param ratio Compression ratio (default: 4.0). For example, 4:1 means 4dB input = 1dB output above threshold.
 * @param kneeDb Soft knee width in dB (default: 6.0). A wider knee makes the compression more gradual.
 * @param attackSeconds Attack time in seconds (default: 0.003). How quickly compression is applied.
 * @param releaseSeconds Release time in seconds (default: 0.1). How quickly compression is released.
 */
class Compressor(
    private val sampleRate: Int,
    thresholdDb: Double = -20.0,
    ratio: Double = 4.0,
    kneeDb: Double = 6.0,
    attackSeconds: Double = 0.003,
    releaseSeconds: Double = 0.1,
) {
    // Compressor parameters (all mutable for real-time changes)
    var thresholdDb: Double = thresholdDb
        set(value) {
            field = value
            updateCoefficients()
        }

    var ratio: Double = ratio.coerceAtLeast(1.0)
        set(value) {
            field = value.coerceAtLeast(1.0)
            updateCoefficients()
        }

    var kneeDb: Double = kneeDb.coerceAtLeast(0.0)
        set(value) {
            field = value.coerceAtLeast(0.0)
            updateCoefficients()
        }

    var attackSeconds: Double = attackSeconds
        set(value) {
            field = value
            updateCoefficients()
        }

    var releaseSeconds: Double = releaseSeconds
        set(value) {
            field = value
            updateCoefficients()
        }

    // Envelope follower state
    private var envelopeDb: Double = -Double.MAX_VALUE

    // Computed coefficients
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0

    init {
        updateCoefficients()
    }

    /**
     * Process a stereo buffer in-place.
     */
    fun process(left: DoubleArray, right: DoubleArray, blockSize: Int) {
        for (i in 0 until blockSize) {
            val inputLevel = max(abs(left[i]), abs(right[i]))

            // Convert to dB (with floor to avoid log(0))
            val inputDb = if (inputLevel > 1e-10) {
                20.0 * ln(inputLevel) / ln(10.0)
            } else {
                -100.0
            }

            // Envelope follower with attack/release
            val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
            envelopeDb = inputDb + coeff * (envelopeDb - inputDb)

            // Calculate gain reduction
            val gainReductionDb = calculateGainReduction(envelopeDb)

            // Convert gain reduction to linear scale
            val gainReduction = if (gainReductionDb < -0.01) {
                exp(gainReductionDb * ln(10.0) / 20.0)
            } else {
                1.0
            }

            // Apply gain reduction
            left[i] *= gainReduction
            right[i] *= gainReduction
        }
    }

    /**
     * Process a mono buffer in-place.
     */
    fun process(buffer: DoubleArray, blockSize: Int) {
        for (i in 0 until blockSize) {
            val inputLevel = abs(buffer[i])

            // Convert to dB
            val inputDb = if (inputLevel > 1e-10) {
                20.0 * ln(inputLevel) / ln(10.0)
            } else {
                -100.0
            }

            // Envelope follower
            val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
            envelopeDb = inputDb + coeff * (envelopeDb - inputDb)

            // Calculate gain reduction
            val gainReductionDb = calculateGainReduction(envelopeDb)

            // Convert to linear
            val gainReduction = if (gainReductionDb < -0.01) {
                exp(gainReductionDb * ln(10.0) / 20.0)
            } else {
                1.0
            }

            // Apply gain reduction
            buffer[i] *= gainReduction
        }
    }

    /**
     * Calculate gain reduction in dB for a given input level in dB.
     * Implements soft knee compression.
     */
    private fun calculateGainReduction(inputDb: Double): Double {
        val overshootDb = inputDb - thresholdDb
        val halfKnee = kneeDb / 2.0

        return when {
            // Below threshold - no compression
            overshootDb < -halfKnee -> 0.0

            // In the knee - soft transition
            overshootDb < halfKnee -> {
                val t = (overshootDb + halfKnee) / kneeDb
                val gainReduction =
                    (1.0 / ratio - 1.0) * (overshootDb + halfKnee) * (overshootDb + halfKnee) / (2.0 * kneeDb)
                gainReduction
            }

            // Above threshold - full compression
            else -> (1.0 / ratio - 1.0) * overshootDb
        }
    }

    /**
     * Update time constants for attack and release.
     */
    private fun updateCoefficients() {
        val attackTime = max(0.0001, attackSeconds)
        val releaseTime = max(0.0001, releaseSeconds)

        attackCoeff = 1.0 - exp(-1.0 / (attackTime * sampleRate))
        releaseCoeff = 1.0 - exp(-1.0 / (releaseTime * sampleRate))
    }

    /**
     * Reset the compressor state.
     */
    fun reset() {
        envelopeDb = -Double.MAX_VALUE
    }

    companion object {
        /**
         * Parse compressor settings from a string.
         * Format: "threshold:ratio:knee:attack:release"
         * Example: "-20:4:6:0.003:0.1"
         *
         * @return CompressorSettings or null if parsing fails
         */
        fun parseSettings(input: String): CompressorSettings? {
            val parts = input.split(":").mapNotNull { it.toDoubleOrNull() }

            return when (parts.size) {
                5 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = parts[2],
                    attackSeconds = parts[3],
                    releaseSeconds = parts[4]
                )

                2 -> CompressorSettings(
                    thresholdDb = parts[0],
                    ratio = parts[1],
                    kneeDb = 6.0,
                    attackSeconds = 0.003,
                    releaseSeconds = 0.1
                )

                else -> null
            }
        }
    }

    data class CompressorSettings(
        val thresholdDb: Double,
        val ratio: Double,
        val kneeDb: Double,
        val attackSeconds: Double,
        val releaseSeconds: Double,
    )
}
