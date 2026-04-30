package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.DB20_OVER_LN10
import io.peekandpoke.klang.audio_be.effects.Compressor.Companion.LN10_OVER_20
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
 * **Topology**: dB-domain one-pole envelope follower (peak link via `max(|L|, |R|)`) feeding a
 * soft-knee parabolic gain curve. Standard musical-compressor design. Used in two places:
 *   1. Per-cylinder musical compressor (user-facing).
 *   2. Master brickwall limiter via `KlangAudioRenderer.kt:20-27`.
 *
 * **Round 5 review (2026-04-30)** kept the topology and applied a code-quality cleanup pass.
 * Deferred-but-known items live in the user's memory at
 * `~/.claude/projects/-opt-dev-peekandpoke-klang/memory/project_filter_review.md`:
 *   - Linear-domain rewrite (would eliminate 2 `exp`/`ln` per sample on top of the constant
 *     caching done here; deferred to the future dedicated `MasterLimiter` work).
 *   - Peak-detector RMS smoothing (secondary britzeling fix; deferred).
 *   - Lookahead support (master-limiter-only feature; deferred to dedicated `MasterLimiter`).
 *
 * @param sampleRate Sample rate in Hz
 * @param thresholdDb Threshold in dB (default: -20.0). Signals above this are compressed.
 * @param ratio Compression ratio (default: 4.0). For example, 4:1 means 4 dB input = 1 dB output above threshold.
 * @param kneeDb Soft knee width in dB (default: 6.0). A wider knee makes the compression more gradual.
 *   `kneeDb = 0` produces a hard-knee gain curve with a C¹ slope kink at the threshold corner;
 *   audible as crackle/sizzle on transient-rich material. Use ≥ 1 dB on brickwall settings.
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
    // Compressor parameters (all mutable for real-time changes; setters silently ignore non-finite values).
    var thresholdDb: Double = guardOr(thresholdDb, -20.0)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
        }

    var ratio: Double = guardOr(ratio, 4.0).coerceAtLeast(1.0)
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceAtLeast(1.0)
            updateCoefficients()
        }

    var kneeDb: Double = guardOr(kneeDb, 6.0).coerceAtLeast(0.0)
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceAtLeast(0.0)
            updateCoefficients()
        }

    var attackSeconds: Double = guardOr(attackSeconds, 0.003)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
        }

    var releaseSeconds: Double = guardOr(releaseSeconds, 0.1)
        set(value) {
            if (!value.isFinite()) return
            field = value
            updateCoefficients()
        }

    /**
     * Make-up gain in decibels to compensate for volume loss after compression.
     */
    var makeupGainDb: Double = 0.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    // Envelope follower state.
    private var envelopeDb: Double = SILENCE_DB

    // Computed coefficients (refreshed on any parameter setter).
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0

    init {
        updateCoefficients()
    }

    /**
     * Process a stereo buffer in-place.
     */
    fun process(left: AudioBuffer, right: AudioBuffer, blockSize: Int) {
        val makeupLinear = computeMakeupLinear()
        for (i in 0 until blockSize) {
            val totalGain = envelopeStep(max(abs(left[i]), abs(right[i]))) * makeupLinear
            left[i] = left[i] * totalGain
            right[i] = right[i] * totalGain
        }
    }

    /**
     * Process a mono buffer in-place.
     */
    fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        val makeupLinear = computeMakeupLinear()
        for (i in 0 until length) {
            val idx = offset + i
            val totalGain = envelopeStep(abs(buffer[idx])) * makeupLinear
            buffer[idx] = buffer[idx] * totalGain
        }
    }

    /**
     * Per-sample envelope-follower + gain-curve step. Inlined into both `process()` overloads
     * so each loop body stays specialized to its input shape (stereo pair vs mono indexed).
     * Uses precomputed [DB20_OVER_LN10] / [LN10_OVER_20] to skip per-sample `ln(10.0)` calls.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun envelopeStep(inputLevel: Double): Double {
        // Convert to dB (with silence floor to avoid log(0)).
        val inputDb = if (inputLevel > SILENCE_LIN) {
            DB20_OVER_LN10 * ln(inputLevel)
        } else {
            SILENCE_DB
        }

        // Envelope follower: one-pole IIR `y += k * (x - y)`, attack vs release coefficient
        // selected by direction of change.
        val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
        envelopeDb += coeff * (inputDb - envelopeDb)

        // Gain curve → linear multiplier. Skip `exp` when reduction is negligible.
        val gainReductionDb = calculateGainReduction(envelopeDb)
        return if (gainReductionDb < -0.01) {
            exp(gainReductionDb * LN10_OVER_20)
        } else {
            1.0
        }
    }

    /** Block-rate makeup-gain linear multiplier; precomputed once per `process()` call. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun computeMakeupLinear(): Double {
        return if (abs(makeupGainDb) > 0.01) exp(makeupGainDb * LN10_OVER_20) else 1.0
    }

    /**
     * Calculate gain reduction in dB for a given input level in dB.
     * Implements soft-knee compression — parabolic blend on `[-halfKnee, +halfKnee]`,
     * verified C¹ continuous at both boundaries when `kneeDb > 0`.
     */
    private fun calculateGainReduction(inputDb: Double): Double {
        val overshootDb = inputDb - thresholdDb
        val halfKnee = kneeDb / 2.0

        return when {
            // Below threshold - no compression
            overshootDb < -halfKnee -> 0.0

            // In the knee - soft transition
            overshootDb < halfKnee -> {
                (1.0 / ratio - 1.0) * (overshootDb + halfKnee) * (overshootDb + halfKnee) / (2.0 * kneeDb)
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
        envelopeDb = SILENCE_DB
    }

    companion object {
        // Compile-time constants for the dB↔linear math — saves one `ln(10.0)` per sample
        // in the hot loop. Full elimination of `exp`/`ln` would require a linear-domain
        // rewrite (deferred — see file KDoc).
        private const val LN10: Double = 2.302585092994046
        private const val DB20_OVER_LN10: Double = 8.685889638065035   // 20 / ln(10)
        private const val LN10_OVER_20: Double = 0.11512925464970229   // ln(10) / 20

        // Envelope follower silence floor.
        private const val SILENCE_DB: Double = -100.0
        private const val SILENCE_LIN: Double = 1e-10

        /** Returns [value] when finite; otherwise [fallback]. Used to keep param setters safe against NaN/Inf. */
        private fun guardOr(value: Double, fallback: Double): Double =
            if (value.isFinite()) value else fallback

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
