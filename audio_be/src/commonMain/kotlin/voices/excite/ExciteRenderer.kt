package io.peekandpoke.klang.audio_be.voices.excite

import io.peekandpoke.klang.audio_be.signalgen.SignalContext
import io.peekandpoke.klang.audio_be.signalgen.SignalGen
import io.peekandpoke.klang.audio_be.voices.BlockContext
import io.peekandpoke.klang.audio_be.voices.BlockRenderer

/**
 * Wraps a [SignalGen] as a [BlockRenderer].
 *
 * Reads pitch modulation from [BlockContext.freqModBuffer] (if [BlockContext.freqModBufferWritten])
 * and generates the raw waveform into [BlockContext.audioBuffer].
 */
class ExciteRenderer(
    private val signal: SignalGen,
    private val signalCtx: SignalContext,
    private val freqHz: Double,
    private val startFrame: Long,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        signalCtx.offset = ctx.offset
        signalCtx.length = ctx.length
        signalCtx.voiceElapsedFrames = (ctx.blockStart - startFrame).toInt()
        signalCtx.phaseMod = if (ctx.freqModBufferWritten) ctx.freqModBuffer else null

        signal.generate(ctx.audioBuffer, freqHz, signalCtx)
    }
}
