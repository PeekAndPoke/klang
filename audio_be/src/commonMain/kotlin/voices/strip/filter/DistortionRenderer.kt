package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Distortion effect with selectable waveshaper shapes.
 *
 * - Exponential drive curve for perceptually even control
 * - Per-shape output normalization to prevent volume jumps
 * - DC blocking filter for asymmetric shapes (diode, rectify)
 * - Optional oversampling to reduce aliasing from nonlinear processing
 */
class DistortionRenderer(
    private val amount: Double,
    shape: String = "soft",
    oversampleStages: Int = 0,
) : BlockRenderer {

    private val drive: Double = 10.0.pow(amount * 1.2)
    private val waveshaper: (Double) -> Double
    private val outputGain: Double
    private val needsDcBlock: Boolean

    private val dcBlockCoeff = 0.995
    private var dcBlockX1 = 0.0
    private var dcBlockY1 = 0.0

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    init {
        val resolved = resolveDistortionShape(shape)
        waveshaper = resolved.fn
        outputGain = resolved.outputGain
        needsDcBlock = resolved.needsDcBlock
    }

    override fun render(ctx: BlockContext) {
        if (amount <= 0.0) return

        val os = oversampler
        if (os != null) {
            renderOversampled(ctx, os)
        } else {
            renderDirect(ctx)
        }
    }

    private fun renderOversampled(ctx: BlockContext, os: Oversampler) {
        val d = drive
        val g = outputGain
        val fn = waveshaper

        os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
            (fn(sample.toDouble() * d) * g).toFloat()
        }

        // DC blocker runs at original rate after decimation
        if (needsDcBlock) {
            applyDcBlock(ctx)
        }
    }

    private fun renderDirect(ctx: BlockContext) {
        val d = drive
        val g = outputGain
        val fn = waveshaper
        val dcBlock = needsDcBlock
        val buf = ctx.audioBuffer

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            val x = buf[idx].toDouble() * d
            var y = fn(x) * g

            if (dcBlock) {
                val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
                dcBlockX1 = y
                dcBlockY1 = flushDenormal(dcOut)
                y = dcOut
            }

            buf[idx] = y.toFloat()
        }
    }

    private fun applyDcBlock(ctx: BlockContext) {
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            val y = buf[idx].toDouble()
            val dcOut = y - dcBlockX1 + dcBlockCoeff * dcBlockY1
            dcBlockX1 = y
            dcBlockY1 = flushDenormal(dcOut)
            buf[idx] = dcOut.toFloat()
        }
    }
}
