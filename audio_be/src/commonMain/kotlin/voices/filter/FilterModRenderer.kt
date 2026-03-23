package io.peekandpoke.klang.audio_be.voices.filter

import io.peekandpoke.klang.audio_be.voices.BlockContext
import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Updates filter cutoff frequencies from envelope modulation.
 * Runs at control rate (once per block) for efficiency.
 */
class FilterModRenderer(
    private val modulators: List<Voice.FilterModulator>,
    private val startFrame: Long,
    private val gateEndFrame: Long,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        for (mod in modulators) {
            val envValue = calculateEnvelope(mod.envelope, ctx)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }
    }

    private fun calculateEnvelope(env: Voice.Envelope, ctx: BlockContext): Double {
        val currentFrame = maxOf(ctx.blockStart, startFrame)
        val absPos = currentFrame - startFrame
        val gateEndPos = gateEndFrame - startFrame

        val envValue = if (absPos >= gateEndPos) {
            // Release phase: calculate the level at gate end, then decay from there
            val levelAtGateEnd = levelAtPosition(env, gateEndPos)
            val relPos = absPos - gateEndPos
            val relRate = if (env.releaseFrames > 0) levelAtGateEnd / env.releaseFrames else 1.0
            levelAtGateEnd - (relPos * relRate)
        } else {
            levelAtPosition(env, absPos)
        }

        return envValue.coerceIn(0.0, 1.0)
    }

    /** Calculate the envelope level at a given position (attack/decay/sustain only). */
    private fun levelAtPosition(env: Voice.Envelope, absPos: Long): Double = when {
        absPos < env.attackFrames -> {
            val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
            absPos * attRate
        }

        absPos < env.attackFrames + env.decayFrames -> {
            val decPos = absPos - env.attackFrames
            val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
            1.0 - (decPos * decRate)
        }

        else -> env.sustainLevel
    }
}
