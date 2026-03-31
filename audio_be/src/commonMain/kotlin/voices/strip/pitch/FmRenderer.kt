package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import io.peekandpoke.klang.audio_be.wrapPhase
import kotlin.math.sin

/**
 * FM (Frequency Modulation) synthesis.
 * Modulates the pitch with a sine oscillator at [Voice.Fm.ratio] * baseFreq,
 * with envelope-controlled depth.
 */
class FmRenderer(
    private val fm: Voice.Fm,
    private val freqHz: Double,
    private val sampleRate: Int,
    private val startFrame: Int,
    private val gateEndFrame: Int,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer

        // Ensure buffer is initialized
        if (!ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) buf[ctx.offset + i] = 1.0
            ctx.freqModBufferWritten = true
        }

        val modFreq = freqHz * fm.ratio
        val modInc = (TWO_PI * modFreq) / sampleRate
        var modPhase = fm.modPhase

        val envLevel = calculateControlRateEnvelope(fm.envelope, ctx.blockStart, startFrame, gateEndFrame)
        val effectiveDepth = fm.depth * envLevel

        for (i in 0 until ctx.length) {
            val modSignal = sin(modPhase) * effectiveDepth
            modPhase += modInc
            val fmMult = 1.0 + (modSignal / freqHz)
            buf[ctx.offset + i] *= fmMult
        }

        fm.modPhase = wrapPhase(modPhase, TWO_PI)
    }
}
