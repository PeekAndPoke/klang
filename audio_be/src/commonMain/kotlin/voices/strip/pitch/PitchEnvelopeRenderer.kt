package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Pitch envelope: attack/decay transient pitch modulation.
 * Creates pitch bends during the voice onset (e.g., drum tuning, synth swoops).
 */
class PitchEnvelopeRenderer(
    private val pitchEnvelope: Voice.PitchEnvelope,
    private val startFrame: Long,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val pEnv = pitchEnvelope

        if (ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] *= calculatePitchMod(ctx.blockStart + idx, pEnv)
            }
        } else {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] = calculatePitchMod(ctx.blockStart + idx, pEnv)
            }
            ctx.freqModBufferWritten = true
        }
    }

    private fun calculatePitchMod(absFrame: Long, pEnv: Voice.PitchEnvelope): Double {
        val relPos = (absFrame - startFrame).toDouble()

        var envLevel = pEnv.anchor

        if (relPos < pEnv.attackFrames) {
            val progress = relPos / pEnv.attackFrames
            envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress
        } else if (relPos < (pEnv.attackFrames + pEnv.decayFrames)) {
            val decayProgress = (relPos - pEnv.attackFrames) / pEnv.decayFrames
            envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress
        }

        return 2.0.pow((pEnv.amount * envLevel) / 12.0)
    }
}
