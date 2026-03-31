package io.peekandpoke.klang.audio_be.voices.strip.excite

import io.peekandpoke.klang.audio_be.exciter.ExciteContext
import io.peekandpoke.klang.audio_be.exciter.Exciter
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Wraps a [Exciter] as a [BlockRenderer].
 *
 * Reads pitch modulation from [BlockContext.freqModBuffer] (if [BlockContext.freqModBufferWritten])
 * and generates the raw waveform into [BlockContext.audioBuffer].
 */
class ExciteRenderer(
    private val signal: Exciter,
    private val signalCtx: ExciteContext,
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
