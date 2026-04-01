package io.peekandpoke.klang.audio_be.voices.strip.ignite

import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Wraps a [Ignitor] as a [BlockRenderer].
 *
 * Reads pitch modulation from [BlockContext.freqModBuffer] (if [BlockContext.freqModBufferWritten])
 * and generates the raw waveform into [BlockContext.audioBuffer].
 */
class IgniteRenderer(
    private val signal: Ignitor,
    private val signalCtx: IgniteContext,
    private val freqHz: Double,
    private val startFrame: Int,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        signalCtx.offset = ctx.offset
        signalCtx.length = ctx.length
        signalCtx.voiceElapsedFrames = ctx.blockStart - startFrame
        signalCtx.phaseMod = if (ctx.freqModBufferWritten) ctx.freqModBuffer else null

        signal.generate(ctx.audioBuffer, freqHz, signalCtx)
    }
}
