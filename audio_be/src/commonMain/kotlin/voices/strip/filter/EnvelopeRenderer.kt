/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.adsrExpNorm
import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_be.envDeclickCoeff
import io.peekandpoke.klang.audio_be.releaseProgressDenom
import io.peekandpoke.klang.audio_be.releaseProgressOffset
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import kotlin.math.ceil
import kotlin.math.floor
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VCA_OFF_TEARDOWN_FADE_SECONDS

/**
 * ADSR amplitude envelope (VCA stage).
 * Multiplies the audio buffer by the envelope value per sample.
 *
 * Per-stage shape curves (Linear/Square/Cube/SCurve/InvSquare/Exponential; default exp) are applied to attack, decay
 * and release independently. All math is multiplies only — no `pow()`, no
 * LUT — so a Square curve adds two multiplies/sample over the linear path.
 *
 * All per-sample arithmetic uses Int to avoid Long boxing on Kotlin/JS.
 * Voice-relative offsets are computed once at the block boundary.
 */
class EnvelopeRenderer(
    private val envelope: Voice.Envelope,
    // Absolute backend frame — Double, see RenderClock.cursorFrame. Relative offsets stay Int.
    private val startFrame: Double,
    private val gateEndFrame: Double,
    // Per-engine VCA character (the PipelineDsl Vca stage). Defaults == the globals,
    // so the built-in engines render byte-for-byte as before.
    private val expK: Double = ADSR_EXP_K,
    private val declickSeconds: Double = ENV_DECLICK_SECONDS,
    /**
     * `false` = this voice's amplitude is not shaped here (the ignitor owns it). The stage still
     * RUNS: see [renderGate]. Resolved before construction, voice over pipeline over `true`.
     */
    private val on: Boolean = true,
) : BlockRenderer {

    // Voice-relative gate end position (Int, avoids Long in per-sample loop)
    private val gateEndPos: Int = (gateEndFrame - startFrame).toInt()

    // Exp-curve normalisation for this engine's curvature (precomputed once per voice).
    private val expNorm: Double = adsrExpNorm(expK)

    override fun render(ctx: BlockContext) {
        if (!on) {
            renderGate(ctx)
            return
        }

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
        // Divides by N-1, so p reaches exactly 1.0 on the LAST frame the voice renders.
        // floor(): releaseFrames is a raw Double, but the voice renders floor(N) frames of release.
        val relFrames = floor(env.releaseFrames)
        val relDenom = releaseProgressDenom(relFrames)
        val relOffset = releaseProgressOffset(relFrames)

        // Per-engine exp curvature, hoisted into locals for the per-sample loop.
        val k = expK
        val norm = expNorm
        // One-pole de-click coefficient for the VCA gain (rounds segment-join corners).
        val declick = envDeclickCoeff(declickSeconds, ctx.sampleRateD)

        // Compute voice-relative position as Int (once per block, not per sample)
        var absPos = (ctx.blockStart + ctx.offset - startFrame).toInt()
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
                val p = ((relPos + relOffset) / relDenom).coerceAtMost(1.0)
                val omp = 1.0 - p
                val shape = when (releaseCurve) {
                    AdsrCurve.Linear -> omp
                    AdsrCurve.Square -> omp * omp
                    AdsrCurve.Cube -> omp * omp * omp
                    AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
                    AdsrCurve.InvSquare -> omp * (2.0 - omp)
                    AdsrCurve.Exponential -> adsrExpShape(omp, k, norm)
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
                            AdsrCurve.Exponential -> adsrExpShape(p, k, norm)
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
                            AdsrCurve.Exponential -> adsrExpShape(omp, k, norm)
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

    /**
     * The `on = false` path: a unity gate with a short linear fade to zero at teardown.
     *
     * **Why the fade exists.** In a VCA-last pipeline (`modern`) the curve drove the fully
     * amplified signal to zero before `Voice.render` dropped the voice. Switching the curve off
     * removes that, and the instrument's own envelope cannot replace it: it sits BEFORE the
     * instrument's amp stages, so a tail it has taken to ~1e-4 comes back out of a tube/drive stage
     * 20 dB louder, and teardown steps that straight to zero. See [VCA_OFF_TEARDOWN_FADE_SECONDS]
     * for the measurements and `VcaOffTeardownSpec` for the guard.
     *
     * **Known limit: this guarantees silence at THIS stage's output, not the voice's.** `pedal`
     * places the VCA second, ahead of Crush / Coarse / Distort / Filter / Tremolo, and those carry
     * state (IIR memory, sample-and-hold) that keeps emitting from a zero input. On such a pipeline
     * the fade removes the gate's own step but not the downstream tail. Closing that properly means
     * a guard at the END of the strip, which would also change the `on = true` path, so it is left
     * as a deliberate follow-up rather than smuggled in here.
     *
     * Note also that the `on = true` path is NOT click-free either — the de-click one-pole lags and
     * leaves ~3.3e-3 on the last frame at a 50 ms release. See the scope note in `AdsrCurveMath`.
     */
    private fun renderGate(ctx: BlockContext) {
        // The LAST frame the voice renders is floor(endFrame)-1. Voice.render clamps vEnd to
        // endFrame and truncates the length, and endFrame is a Double (gateEnd + release*sampleRate,
        // fractional at most sample rates), so the frame AT endFrame never exists — targeting it
        // would leave frac(endFrame)/fadeDen behind, the same residual class this fade removes.
        //
        // PRECONDITION: startFrame and blockStart are integral Doubles (VoiceFactory floors the
        // former, the worklet advances cursorFrame by whole blocks). That is what makes
        // `offset + length - 1 == floor(endFrame) - blockStart - 1` hold even when a voice starts
        // and ends inside one block, and hence what makes the endpoint exact. `VoiceFactoryVcaOffSpec`
        // renders a REAL Voice to pin the coupling; if sub-sample onsets ever arrive, revisit here.
        val lastFrame = floor(ctx.endFrame) - 1.0
        val fadeFrames = VCA_OFF_TEARDOWN_FADE_SECONDS * ctx.sampleRateD
        // The guard always gets its full window. An earlier version preferred to start at gate end
        // so it could not touch the note body, but that collapsed the window for a SHORT non-zero
        // release: at 0.1 ms the ramp got 4 frames and the last sample came out at 0.21 of full
        // scale — a step, i.e. the click this exists to remove, and worse than release = 0 got.
        // Taking the window from the gate tail when the release is too short is the lesser evil.
        val ideal = lastFrame - (fadeFrames - 1.0)
        // ...but never more than the second half of the voice: this is a fade guard, not an envelope.
        val midpoint = (ctx.startFrame + lastFrame) * 0.5
        val fadeStart = maxOf(ideal, midpoint).coerceAtMost(lastFrame)
        // A reciprocal is safe here, unlike the release ramp: the endpoint that must be exact is
        // `remaining == 0.0`, which comes from `lastIdx - idx == 0`, and 0.0 * x is 0.0 exactly.
        // The `> 1.0` clamp absorbs a 1-ulp overshoot at the entry frame.
        val fadeScale = 1.0 / (lastFrame - fadeStart).coerceAtLeast(1.0)

        // Hoisted per block so the per-sample path stays an Int compare, per this class's KDoc.
        val lastIdx = lastFrame - ctx.blockStart
        val fadeStartIdx = ceil(fadeStart - ctx.blockStart).toInt()

        // No de-click smoother here, and this is REQUIRED, not merely free: inside the window the
        // target ramps 1.0 -> 0, so a one-pole would lag and leave a non-zero final sample, losing
        // the exact-zero endpoint that is the whole point. Outside the window its target is a
        // constant 1.0 and Voice.Envelope.of always starts unprimed, so it would do nothing anyway.
        //
        // Skip the note body entirely: `from` collapses the per-sample branch and, for every block
        // before the ramp, the loop does not run at all. maxOf also absorbs the very negative
        // fadeStartIdx that ceil().toInt() produces on later blocks.
        val end = ctx.offset + ctx.length
        val from = maxOf(ctx.offset, fadeStartIdx)
        for (idx in from until end) {
            val remaining = (lastIdx - idx) * fadeScale
            val gain = if (remaining < 0.0) 0.0 else if (remaining > 1.0) 1.0 else remaining
            ctx.audioBuffer[idx] = ctx.audioBuffer[idx] * gain
        }

        // Deliberately NOT writing env.level / smoothedLevel / smoothPrimed. The Envelope instance
        // is shared by every Vca stage in the pipeline (FilterPipelineBuilder hands the same one to
        // each), so priming it here would make a LATER ADSR Vca skip its own seeding and render the
        // note ONSET fading down from unity. A pipeline like
        // `vca().on(false) -> distort -> vca()` is reachable from KlangScript.
    }
}
