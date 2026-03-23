package io.peekandpoke.klang.audio_be.voices.pitch

import io.peekandpoke.klang.audio_be.voices.BlockContext
import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice
import kotlin.math.pow

/**
 * Pitch glide/acceleration over the voice's lifetime.
 * Exponential pitch change: the pitch shifts by [Voice.Accelerate.amount] semitones
 * over the full voice duration.
 */
class AccelerateRenderer(
    private val accelerate: Voice.Accelerate,
    private val startFrame: Long,
    private val endFrame: Long,
) : BlockRenderer {

    private val totalFrames = (endFrame - startFrame).toDouble()

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val amount = accelerate.amount

        if (ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                val absFrame = ctx.blockStart + idx
                val progress = (absFrame - startFrame).toDouble() / totalFrames
                buf[idx] *= 2.0.pow(amount * progress)
            }
        } else {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                val absFrame = ctx.blockStart + idx
                val progress = (absFrame - startFrame).toDouble() / totalFrames
                buf[idx] = 2.0.pow(amount * progress)
            }
            ctx.freqModBufferWritten = true
        }
    }
}
