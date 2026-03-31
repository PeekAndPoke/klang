package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Pitch envelope: attack/decay transient pitch modulation.
 * Creates pitch bends during the voice onset (e.g., drum tuning, synth swoops).
 *
 * All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
 */
class PitchEnvelopeRenderer(
    private val pitchEnvelope: Voice.PitchEnvelope,
    private val startFrame: Int,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val buf = ctx.freqModBuffer
        val pEnv = pitchEnvelope

        // Compute voice-relative position as Int (once per block)
        val blockRelStart = (ctx.blockStart + ctx.offset) - startFrame

        if (ctx.freqModBufferWritten) {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] *= calculatePitchMod(blockRelStart + i, pEnv)
            }
        } else {
            for (i in 0 until ctx.length) {
                val idx = ctx.offset + i
                buf[idx] = calculatePitchMod(blockRelStart + i, pEnv)
            }
            ctx.freqModBufferWritten = true
        }
    }

    private fun calculatePitchMod(relPos: Int, pEnv: Voice.PitchEnvelope): Double {
        val relPosD = relPos.toDouble()

        var envLevel = pEnv.anchor

        if (relPosD < pEnv.attackFrames) {
            val progress = relPosD / pEnv.attackFrames
            envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress
        } else if (relPosD < (pEnv.attackFrames + pEnv.decayFrames)) {
            val decayProgress = (relPosD - pEnv.attackFrames) / pEnv.decayFrames
            envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress
        }

        return 2.0.pow((pEnv.amount * envLevel) / 12.0)
    }
}
