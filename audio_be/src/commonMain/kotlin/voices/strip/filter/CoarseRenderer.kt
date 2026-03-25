package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Sample rate reducer (coarse) effect — holds a sample value for multiple frames.
 * Creates a lo-fi digital sound by reducing the effective sample rate.
 */
class CoarseRenderer(private val amount: Double) : BlockRenderer {

    private var lastValue: Float = 0.0f
    private var counter: Double = 0.0
    private val increment: Double = 1.0 / amount

    override fun render(ctx: BlockContext) {
        if (amount <= 1.0) return

        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            if (counter >= 1.0 || (i == 0 && counter == 0.0)) {
                lastValue = buf[idx]
                counter -= 1.0
            }

            buf[idx] = lastValue
            counter += increment
        }
    }
}
