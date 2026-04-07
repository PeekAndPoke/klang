package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow
import kotlin.math.sin

/**
 * LFO pitch modulation (vibrato).
 * Writes periodic pitch multipliers to [BlockContext.freqModBuffer].
 */
class VibratoRenderer(
    private val vibrato: Voice.Vibrato,
    private val sampleRate: Int,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val phaseInc = (TWO_PI * vibrato.rate) / sampleRate
        val depthSemitones = vibrato.depth
        var phase = vibrato.phase

        if (ctx.freqModBufferWritten) {
            // Multiply into existing modulation
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                // Equal temperament: symmetric, never negative
                buf[idx] *= 2.0.pow(sin(phase) * depthSemitones / 12.0)
                phase += phaseInc
            }
        } else {
            // First pitch renderer — write directly
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] = 2.0.pow(sin(phase) * depthSemitones / 12.0)
                phase += phaseInc
            }
            ctx.freqModBufferWritten = true
        }

        if (phase >= TWO_PI) phase -= TWO_PI
        vibrato.phase = phase
    }
}
