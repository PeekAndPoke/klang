package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_be.envDeclickCoeff
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * ADSR amplitude envelope (VCA stage).
 * Multiplies the audio buffer by the envelope value per sample.
 *
 * Per-stage shape curves (Linear/Square/Cube) are applied to attack, decay
 * and release independently. All math is multiplies only — no `pow()`, no
 * LUT — so a Square curve adds two multiplies/sample over the linear path.
 *
 * All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
 * Voice-relative offsets are computed once at the block boundary.
 */
class EnvelopeRenderer(
    private val envelope: Voice.Envelope,
    private val startFrame: Int,
    private val gateEndFrame: Int,
) : BlockRenderer {

    // Voice-relative gate end position (Int, avoids Long in per-sample loop)
    private val gateEndPos: Int = gateEndFrame - startFrame

    override fun render(ctx: BlockContext) {
        val env = envelope
        val sustain = env.sustainLevel
        val attackFrames = env.attackFrames
        val decayFrames = env.decayFrames
        val attDecFrames = attackFrames + decayFrames
        val attackCurve = env.attackCurve
        val decayCurve = env.decayCurve
        val releaseCurve = env.releaseCurve

        val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
        val decRate = if (decayFrames > 0) 1.0 / decayFrames else 1.0
        val relRateDen = if (env.releaseFrames > 0) env.releaseFrames else 1.0

        // One-pole de-click coefficient for the VCA gain (rounds segment-join corners).
        val declick = envDeclickCoeff(ctx.sampleRateD)

        // Compute voice-relative position as Int (once per block, not per sample)
        var absPos = (ctx.blockStart + ctx.offset) - startFrame
        var currentEnv = env.level
        var smoothed = env.smoothedLevel

        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            if (absPos >= gateEndPos) {
                // Release phase: level = releaseStartLevel * shape(1 - p)
                if (!env.releaseStarted) {
                    env.releaseStartLevel = currentEnv
                    env.releaseStarted = true
                }
                val relPos = absPos - gateEndPos
                val p = (relPos / relRateDen).coerceAtMost(1.0)
                val omp = 1.0 - p
                val shape = when (releaseCurve) {
                    AdsrCurve.Linear -> omp
                    AdsrCurve.Square -> omp * omp
                    AdsrCurve.Cube -> omp * omp * omp
                    AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
                    AdsrCurve.InvSquare -> omp * (2.0 - omp)
                    AdsrCurve.Exponential -> adsrExpShape(omp)
                }
                currentEnv = env.releaseStartLevel * shape
            } else {
                env.releaseStarted = false
                currentEnv = when {
                    // Attack: level = shape(p)
                    absPos < attackFrames -> {
                        val p = absPos * attRate
                        when (attackCurve) {
                            AdsrCurve.Linear -> p
                            AdsrCurve.Square -> p * p
                            AdsrCurve.Cube -> p * p * p
                            AdsrCurve.SCurve -> if (p < 0.5) 2.0 * p * p else 1.0 - 2.0 * (1.0 - p) * (1.0 - p)
                            AdsrCurve.InvSquare -> p * (2.0 - p)
                            AdsrCurve.Exponential -> adsrExpShape(p)
                        }
                    }
                    // Decay: level = sustain + (1 - sustain) * shape(1 - p)
                    absPos < attDecFrames -> {
                        val decPos = absPos - attackFrames
                        val p = decPos * decRate
                        val omp = 1.0 - p
                        val shape = when (decayCurve) {
                            AdsrCurve.Linear -> omp
                            AdsrCurve.Square -> omp * omp
                            AdsrCurve.Cube -> omp * omp * omp
                            AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
                            AdsrCurve.InvSquare -> omp * (2.0 - omp)
                            AdsrCurve.Exponential -> adsrExpShape(omp)
                        }
                        sustain + (1.0 - sustain) * shape
                    }

                    else -> sustain
                }
            }

            if (currentEnv < 0.0) currentEnv = 0.0

            // Seed the smoother to the first rendered gain so always-on voices and
            // the note onset are not faded in; then round subsequent corners.
            if (!env.smoothPrimed) {
                smoothed = currentEnv
                env.smoothPrimed = true
            }
            smoothed += declick * (currentEnv - smoothed)

            ctx.audioBuffer[idx] = (ctx.audioBuffer[idx] * smoothed)
            absPos++
        }

        // Update envelope state
        env.level = currentEnv
        env.smoothedLevel = smoothed
    }
}
