package io.peekandpoke.klang.audio_be.ignitor

/**
 * ADSR amplitude envelope combinator.
 *
 * Multiplies the signal by a time-varying gain envelope:
 * - Attack:  ramp from 0.0 to 1.0 over [attackSec]
 * - Decay:   ramp from 1.0 to [sustainLevel] over [decaySec]
 * - Sustain: hold at [sustainLevel] until gate ends
 * - Release: ramp from current level to 0.0 over [releaseSec]
 *
 * Voice timing (gate end, release duration) is read from [IgniteContext].
 * The envelope does NOT control voice lifecycle — that's handled by frame counting in Voice.
 *
 * Ported from: Voice.applyEnvelope() in voices/Voice.kt
 */
fun Ignitor.adsr(
    attackSec: Ignitor,
    decaySec: Ignitor,
    sustainLevel: Ignitor,
    releaseSec: Ignitor,
): Ignitor {
    var currentLevel = 0.0
    var releaseStartLevel = 0.0
    var releaseStarted = false

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { input ->
            this.generate(input, freqHz, ctx)

            val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx).coerceAtLeast(0.0)
            val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx).coerceAtLeast(0.0)
            val sustainLevelVal = Ignitors.readParam(sustainLevel, freqHz, ctx).coerceIn(0.0, 1.0)
            val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx).coerceAtLeast(0.0)

            val attackFrames = (attackSecVal * ctx.sampleRate).toInt()
            val decayFrames = (decaySecVal * ctx.sampleRate).toInt()
            val gateEndPos = ctx.gateEndFrame

            val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
            val decRate = if (decayFrames > 0) (1.0 - sustainLevelVal) / decayFrames else 0.0
            val releaseFrames = (releaseSecVal * ctx.sampleRate).toInt()
            val relDenom = if (releaseFrames > 0) releaseFrames.toDouble() else 1.0

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

                        else -> sustainLevelVal
                    }
                }

                if (currentLevel < 0.0) currentLevel = 0.0

                output[i] = (input[i] * currentLevel).toFloat()
                absPos++
            }
        }
    }
}

/** Double convenience overload — delegates to the Ignitor-param version. */
fun Ignitor.adsr(
    attackSec: Double,
    decaySec: Double,
    sustainLevel: Double,
    releaseSec: Double,
): Ignitor = adsr(
    ParamIgnitor("attackSec", attackSec),
    ParamIgnitor("decaySec", decaySec),
    ParamIgnitor("sustainLevel", sustainLevel),
    ParamIgnitor("releaseSec", releaseSec),
)
