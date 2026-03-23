package io.peekandpoke.klang.audio_be.voices.filter

import io.peekandpoke.klang.audio_be.voices.BlockContext
import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * ADSR amplitude envelope (VCA stage).
 * Multiplies the audio buffer by the envelope value per sample.
 */
class EnvelopeRenderer(
    private val envelope: Voice.Envelope,
    private val startFrame: Long,
    private val gateEndFrame: Long,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        val env = envelope
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
        val relRateDen = if (env.releaseFrames > 0) env.releaseFrames else 1.0

        var absPos = (ctx.blockStart + ctx.offset) - startFrame
        val gateEndPos = gateEndFrame - startFrame
        var currentEnv = env.level

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            if (absPos >= gateEndPos) {
                // Release phase
                if (!env.releaseStarted) {
                    env.releaseStartLevel = currentEnv
                    env.releaseStarted = true
                }
                val relPos = absPos - gateEndPos
                val relRate = env.releaseStartLevel / relRateDen
                currentEnv = env.releaseStartLevel - (relPos * relRate)
            } else {
                // Attack/Decay/Sustain phases
                env.releaseStarted = false
                currentEnv = when {
                    absPos < env.attackFrames -> absPos * attRate
                    absPos < env.attackFrames + env.decayFrames -> {
                        val decPos = absPos - env.attackFrames
                        1.0 - (decPos * decRate)
                    }

                    else -> env.sustainLevel
                }
            }

            if (currentEnv < 0.0) currentEnv = 0.0

            ctx.audioBuffer[idx] = (ctx.audioBuffer[idx] * currentEnv).toFloat()
            absPos++
        }

        // Update envelope state
        env.level = currentEnv
    }
}
