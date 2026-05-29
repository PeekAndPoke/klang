package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * ADSR amplitude envelope combinator.
 *
 * Multiplies the signal by a time-varying gain envelope. Each stage has its
 * own shape curve (Linear/Square/Cube):
 * - Attack:  ramps from 0.0 to 1.0 over [attackSec], shape via [attackCurve]
 * - Decay:   ramps from 1.0 to [sustainLevel] over [decaySec], shape via [decayCurve]
 * - Sustain: holds at [sustainLevel] until gate ends
 * - Release: ramps from current level to 0.0 over [releaseSec], shape via [releaseCurve]
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
    attackCurve: AdsrCurve = AdsrCurve.SCurve,
    decayCurve: AdsrCurve = AdsrCurve.Square,
    releaseCurve: AdsrCurve = AdsrCurve.Square,
): Ignitor = AdsrIgnitor(
    this, attackSec, decaySec, sustainLevel, releaseSec,
    attackCurve, decayCurve, releaseCurve,
)

private class AdsrIgnitor(
    private val upstream: Ignitor,
    private val attackSec: Ignitor,
    private val decaySec: Ignitor,
    private val sustainLevel: Ignitor,
    private val releaseSec: Ignitor,
    private val attackCurve: AdsrCurve,
    private val decayCurve: AdsrCurve,
    private val releaseCurve: AdsrCurve,
) : Ignitor {
    private var currentLevel: Double = 0.0
    private var releaseStartLevel: Double = 0.0
    private var releaseStarted: Boolean = false

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx).coerceAtLeast(0.0)
            val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx).coerceAtLeast(0.0)
            val sustainLevelVal = Ignitors.readParam(sustainLevel, freqHz, ctx).coerceIn(0.0, 1.0)
            val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx).coerceAtLeast(0.0)

            val attackFrames = (attackSecVal * ctx.sampleRate).toInt()
            val decayFrames = (decaySecVal * ctx.sampleRate).toInt()
            val attDecFrames = attackFrames + decayFrames
            val gateEndPos = ctx.gateEndFrame

            val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
            val decRate = if (decayFrames > 0) 1.0 / decayFrames else 1.0
            val releaseFrames = (releaseSecVal * ctx.sampleRate).toInt()
            val relDenom = if (releaseFrames > 0) releaseFrames.toDouble() else 1.0

            val attCurve = attackCurve
            val decCurve = decayCurve
            val relCurve = releaseCurve

            var absPos = ctx.voiceElapsedFrames

            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                if (absPos >= gateEndPos) {
                    if (!releaseStarted) {
                        releaseStartLevel = currentLevel
                        releaseStarted = true
                    }
                    val relPos = absPos - gateEndPos
                    val p = (relPos / relDenom).coerceAtMost(1.0)
                    val omp = 1.0 - p
                    val shape = when (relCurve) {
                        AdsrCurve.Linear -> omp
                        AdsrCurve.Square -> omp * omp
                        AdsrCurve.Cube -> omp * omp * omp
                        AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
                        AdsrCurve.InvSquare -> omp * (2.0 - omp)
                    }
                    currentLevel = releaseStartLevel * shape
                } else {
                    releaseStarted = false
                    currentLevel = when {
                        absPos < attackFrames -> {
                            val p = absPos * attRate
                            when (attCurve) {
                                AdsrCurve.Linear -> p
                                AdsrCurve.Square -> p * p
                                AdsrCurve.Cube -> p * p * p
                                AdsrCurve.SCurve -> if (p < 0.5) 2.0 * p * p else 1.0 - 2.0 * (1.0 - p) * (1.0 - p)
                                AdsrCurve.InvSquare -> p * (2.0 - p)
                            }
                        }

                        absPos < attDecFrames -> {
                            val decPos = absPos - attackFrames
                            val p = decPos * decRate
                            val omp = 1.0 - p
                            val shape = when (decCurve) {
                                AdsrCurve.Linear -> omp
                                AdsrCurve.Square -> omp * omp
                                AdsrCurve.Cube -> omp * omp * omp
                                AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
                                AdsrCurve.InvSquare -> omp * (2.0 - omp)
                            }
                            sustainLevelVal + (1.0 - sustainLevelVal) * shape
                        }
                        else -> sustainLevelVal
                    }
                }

                if (currentLevel < 0.0) currentLevel = 0.0

                buffer[i] = (input[i] * currentLevel)
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
    attackCurve: AdsrCurve = AdsrCurve.SCurve,
    decayCurve: AdsrCurve = AdsrCurve.Square,
    releaseCurve: AdsrCurve = AdsrCurve.Square,
): Ignitor = adsr(
    ParamIgnitor("attackSec", attackSec),
    ParamIgnitor("decaySec", decaySec),
    ParamIgnitor("sustainLevel", sustainLevel),
    ParamIgnitor("releaseSec", releaseSec),
    attackCurve, decayCurve, releaseCurve,
)
