package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.floor
import kotlin.math.pow

/**
 * BitCrush effect — reduces bit depth for a lo-fi digital sound.
 * Quantizes the amplitude to discrete levels.
 *
 * Optional oversampling reduces aliasing from the staircase quantization —
 * opt in via the `oversampleStages` constructor param. Default is raw (no oversampling),
 * which preserves the classic aliased character.
 */
class CrushRenderer(amount: Double, oversampleStages: Int = 0) : BlockRenderer {

    private val levels: Double = if (amount > 0.0) 2.0.pow(amount).toInt().toDouble() else 0.0
    private val halfLevels: Double = levels / 2.0

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    override fun render(ctx: BlockContext) {
        if (levels <= 1.0) return

        val os = oversampler
        if (os != null) {
            renderOversampled(ctx, os)
        } else {
            renderDirect(ctx)
        }
    }

    private fun renderDirect(ctx: BlockContext) {
        val hl = halfLevels
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            buf[idx] = (floor(buf[idx].toDouble() * hl) / hl).toFloat()
        }
    }

    private fun renderOversampled(ctx: BlockContext, os: Oversampler) {
        val hl = halfLevels
        os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
            (floor(sample.toDouble() * hl) / hl).toFloat()
        }
    }
}
