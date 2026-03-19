package io.peekandpoke.klang.audio_be.filters.effects

import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import kotlin.math.pow

/**
 * Distortion effect with selectable waveshaper shapes.
 *
 * Features:
 * - Exponential drive curve for perceptually even control
 * - Per-shape output normalization to prevent volume jumps
 * - DC blocking filter for asymmetric shapes (diode, rectify)
 * - Drive parameter smoothing to prevent clicks
 *
 * @param amount Distortion amount (0.0 = clean, 1.0+ = heavily distorted).
 * @param shape Waveshaper type: soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, exp.
 */
class DistortionFilter(
    private val amount: Double,
    shape: String = "soft",
) : AudioFilter {

    /** Exponential drive curve: amount=0 -> 1x, amount=1 -> ~15.8x */
    private val drive: Double = 10.0.pow(amount * 1.2)

    /** Resolved waveshaper function (avoids per-sample when dispatch) */
    private val waveshaper: (Double) -> Double

    /** Output normalization factor (compensates for different peak levels per shape) */
    private val outputGain: Double

    /** Whether this shape needs DC blocking (asymmetric types) */
    private val needsDcBlock: Boolean

    // DC blocker state: y[n] = x[n] - x[n-1] + coeff * y[n-1]
    private val dcBlockCoeff = 0.995
    private var dcBlockX1 = 0.0
    private var dcBlockY1 = 0.0

    init {
        val resolved = resolveShape(shape)
        waveshaper = resolved.fn
        outputGain = resolved.outputGain
        needsDcBlock = resolved.needsDcBlock
    }

    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        if (amount <= 0.0) {
            return
        }

        val d = drive
        val g = outputGain
        val fn = waveshaper
        val dcBlock = needsDcBlock

        for (i in 0 until length) {
            val idx = offset + i
            val x = buffer[idx] * d
            var y = fn(x) * g

            if (dcBlock) {
                // One-pole highpass DC blocker (~5 Hz at 48kHz)
                val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
                dcBlockX1 = y
                dcBlockY1 = dcOut
                y = dcOut
            }

            buffer[idx] = y
        }
    }

    companion object {
        private data class ResolvedShape(
            val fn: (Double) -> Double,
            val outputGain: Double = 1.0,
            val needsDcBlock: Boolean = false,
        )

        private fun resolveShape(shape: String): ResolvedShape = when (shape.lowercase()) {
            "hard" -> ResolvedShape(fn = ClippingFuncs::hardClip)
            "gentle" -> ResolvedShape(fn = ClippingFuncs::softClip, outputGain = 2.0)
            "cubic" -> ResolvedShape(fn = ClippingFuncs::cubicClip)
            "diode" -> ResolvedShape(fn = ClippingFuncs::diodeClip, needsDcBlock = true)
            "fold" -> ResolvedShape(fn = ClippingFuncs::sineFold)
            "chebyshev" -> ResolvedShape(fn = ClippingFuncs::chebyshevT3)
            "rectify" -> ResolvedShape(fn = ClippingFuncs::rectify, needsDcBlock = true)
            "exp" -> ResolvedShape(fn = ClippingFuncs::expClip)
            else -> ResolvedShape(fn = ClippingFuncs::fastTanh) // "soft" and fallback
        }
    }
}
