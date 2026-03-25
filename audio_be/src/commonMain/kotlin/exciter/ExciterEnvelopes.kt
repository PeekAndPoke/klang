package io.peekandpoke.klang.audio_be.exciter

/**
 * ADSR amplitude envelope combinator.
 *
 * Multiplies the signal by a time-varying gain envelope:
 * - Attack:  ramp from 0.0 to 1.0 over [attackSec]
 * - Decay:   ramp from 1.0 to [sustainLevel] over [decaySec]
 * - Sustain: hold at [sustainLevel] until gate ends
 * - Release: ramp from current level to 0.0 over [releaseSec]
 *
 * Voice timing (gate end, release duration) is read from [ExciteContext].
 * The envelope does NOT control voice lifecycle — that's handled by frame counting in Voice.
 *
 * Ported from: Voice.applyEnvelope() in voices/Voice.kt
 */
fun Exciter.adsr(
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
): Exciter {
    var currentLevel = 0.0
    var releaseStartLevel = 0.0
    var releaseStarted = false

    return Exciter { buffer, freqHz, ctx ->
        this.generate(buffer, freqHz, ctx)

        val attackFrames = (attackSec * ctx.sampleRate).toInt()
        val decayFrames = (decaySec * ctx.sampleRate).toInt()
        val gateEndPos = ctx.gateEndFrame

        val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
        val decRate = if (decayFrames > 0) (1.0 - sustainLevel) / decayFrames else 0.0
        val relDenom = if (ctx.releaseFrames > 0) ctx.releaseFrames.toDouble() else 1.0

        var absPos = ctx.voiceElapsedFrames

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            if (absPos >= gateEndPos) {
                // Release phase
                if (!releaseStarted) {
                    releaseStartLevel = currentLevel
                    releaseStarted = true
                }
                val relPos = absPos - gateEndPos
                val relRate = releaseStartLevel / relDenom
                currentLevel = releaseStartLevel - (relPos * relRate)
            } else {
                // Attack / Decay / Sustain
                releaseStarted = false
                currentLevel = when {
                    absPos < attackFrames -> absPos * attRate
                    absPos < attackFrames + decayFrames -> {
                        val decPos = absPos - attackFrames
                        1.0 - (decPos * decRate)
                    }

                    else -> sustainLevel
                }
            }

            if (currentLevel < 0.0) currentLevel = 0.0

            buffer[i] = (buffer[i] * currentLevel).toFloat()
            absPos++
        }
    }
}
