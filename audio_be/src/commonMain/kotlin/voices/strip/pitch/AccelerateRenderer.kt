package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Pitch glide/acceleration over the voice's lifetime.
 * Exponential pitch change: the pitch shifts by [Voice.Accelerate.amount] semitones
 * over the full voice duration.
 *
 * Uses multiplicative stepping: one pow() per block, then per-sample multiply.
 * All per-sample arithmetic uses Int/Double to avoid Long boxing on Kotlin/JS.
 */
class AccelerateRenderer(
    private val accelerate: Voice.Accelerate,
    private val startFrame: Long,
    private val endFrame: Long,
) : BlockRenderer {

    private val totalFrames = (endFrame - startFrame).toDouble()

    // Per-sample multiplicative step: ratio = 2^(amount / totalFrames)
    private val step = 2.0.pow(accelerate.amount / totalFrames)

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer

        // Seed with pow() once at the block start, then multiply per sample
        val blockRelStart = ((ctx.blockStart + ctx.offset) - startFrame).toInt()
        var ratio = 2.0.pow(accelerate.amount * blockRelStart.toDouble() / totalFrames)

        if (ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) {
                buf[ctx.offset + i] *= ratio
                ratio *= step
            }
        } else {
            for (i in 0 until ctx.length) {
                buf[ctx.offset + i] = ratio
                ratio *= step
            }
            ctx.freqModBufferWritten = true
        }
    }
}
