/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ADSR_EXP_NORM
import io.peekandpoke.klang.audio_be.adsrCurveShape
import io.peekandpoke.klang.audio_be.envDeclickCoeff
import io.peekandpoke.klang.audio_be.releaseProgressOffset
import io.peekandpoke.klang.audio_be.releaseProgressDenom
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import io.peekandpoke.klang.audio_bridge.constants.ADSR_SUSTAIN_LEVEL

/**
 * ADSR amplitude envelope combinator.
 *
 * Multiplies the signal by a time-varying gain envelope. Each stage has its
 * own shape curve (Linear/Square/Cube/SCurve/InvSquare/Exponential; default [AdsrCurve.Default] = exp):
 * - Attack:  ramps from 0.0 to 1.0 over [attackSec], shape via [attackCurve]
 *   (curves: Linear/Square/Cube/SCurve/InvSquare/Exponential — default [AdsrCurve.Default] = exp)
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
    attackCurve: AdsrCurve = AdsrCurve.Default,
    decayCurve: AdsrCurve = AdsrCurve.Default,
    releaseCurve: AdsrCurve = AdsrCurve.Default,
    declickSeconds: Ignitor = ParamIgnitor("declickSeconds", 0.0),
): Ignitor = AdsrIgnitor(
    this, attackSec, decaySec, sustainLevel, releaseSec,
    attackCurve, decayCurve, releaseCurve,
    declickSeconds,
)

/**
 * [value] when it is finite, [fallback] when it is not.
 *
 * **Not a safety clamp, and the Motor stays raw.** Every finite value passes through untouched,
 * negative and enormous ones included, so no character is taken away. What it closes is a NaN
 * SOURCE that the build-time gate cannot reach: the gate switches a stage off when its knob
 * resolves non-finite, but the envelope is gated only by its ON/OFF switch, for which unset means
 * ON (the strip's VCA runs on every voice today), so an authored `Constant(NaN)` or a slot whose
 * default is the sentinel arrives here intact. One knob then carries it into the samples:
 * [AdsrIgnitor.sustainLevel], because `coerceIn(0.0, 1.0)` is the identity on a NaN and the level
 * multiplies every sample. (`expK` was the second until phase 3 step 3c removed that knob; every
 * exponential stage now bends at [ADSR_EXP_K].)
 *
 * It is NOT merely "completing the existing coercion", and the difference is worth naming: on a
 * NaN the substitution is the only thing that does anything, but on an INFINITY `coerceIn` already
 * had an answer and this overrules it. `+Inf` used to sustain at 1.0 and `-Inf` at 0.0; both now
 * sustain at [ADSR_SUSTAIN_LEVEL]. That is deliberate and it is the house rule, not a slip: a
 * non-finite value reads as UNSET (`/dsl-design` section 4), which is exactly how the build-time
 * gate one file over reads every knob it tests, and an unset knob takes its default rather than a
 * rail. `IgnitorGateSpec` pins both infinities.
 *
 * The other knobs need nothing: the two times become frame counts through
 * `(seconds * sampleRate).toInt()`, and `Double.toInt()` of a NaN is 0 on both platforms, so a
 * NaN-timed stage simply has no frames; `declickSeconds` is read through `> 0.0`, which a NaN
 * fails, so a non-finite de-click is off. `releaseSec`'s other half, the voice's release TAIL,
 * is nulled where the build reads it (`IgnitorDslRuntime`'s Adsr arm).
 *
 * The pitch envelope's sustain (`PitchModFactories`) takes the same substitution for the same
 * reason: it multiplies every ratio of a settled block, so a NaN would freeze the pitch.
 */
internal fun finiteOr(value: Double, fallback: Double): Double = if (value.isFinite()) value else fallback

private class AdsrIgnitor(
    private val upstream: Ignitor,
    private val attackSec: Ignitor,
    private val decaySec: Ignitor,
    private val sustainLevel: Ignitor,
    private val releaseSec: Ignitor,
    private val attackCurve: AdsrCurve,
    private val decayCurve: AdsrCurve,
    private val releaseCurve: AdsrCurve,
    private val declickSeconds: Ignitor,
) : Ignitor {
    private var currentLevel: Double = 0.0
    private var releaseStartLevel: Double = 0.0
    private var releaseStarted: Boolean = false

    // Opt-in de-click one-pole on the output gain, primed to the first rendered level.
    private var smoothedLevel: Double = 0.0
    private var smoothPrimed: Boolean = false

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx).coerceAtLeast(0.0)
            val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx).coerceAtLeast(0.0)
            // finiteOr BEFORE the coercion. The order is invisible on a NaN (`coerceIn` is the
            // identity on one, so either order ends at the default) and decides the INFINITIES:
            // before, they take the default; after, they would take the rail `coerceIn` puts them
            // on. Unset reads as unset, not as a rail. See the note on `finiteOr`.
            val sustainLevelVal = finiteOr(Ignitors.readParam(sustainLevel, freqHz, ctx), ADSR_SUSTAIN_LEVEL)
                .coerceIn(0.0, 1.0)
            val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx).coerceAtLeast(0.0)

            // declick is a control-rate slot: read per block, derive its coefficient once here.
            val declickSecondsVal = Ignitors.readParam(declickSeconds, freqHz, ctx)
            val declickOn = declickSecondsVal > 0.0
            val declickCoeff = if (declickOn) envDeclickCoeff(declickSecondsVal, ctx.sampleRateD) else 0.0
            // Every exponential stage bends at ADSR_EXP_K (the per-envelope knob was removed in 3c).
            val expKVal = ADSR_EXP_K
            val expNorm = ADSR_EXP_NORM

            val attackFrames = (attackSecVal * ctx.sampleRate).toInt()
            val decayFrames = (decaySecVal * ctx.sampleRate).toInt()
            val attDecFrames = attackFrames + decayFrames
            val gateEndPos = ctx.gateEndFrame

            val attRate = if (attackFrames > 0) 1.0 / attackFrames else 1.0
            val decRate = if (decayFrames > 0) 1.0 / decayFrames else 1.0
            val releaseFrames = (releaseSecVal * ctx.sampleRate).toInt()
            val relDenom = releaseProgressDenom(releaseFrames.toDouble())
            val relOffset = releaseProgressOffset(releaseFrames.toDouble())

            val attCurve = attackCurve
            val decCurve = decayCurve
            val relCurve = releaseCurve

            var absPos = ctx.voiceElapsedFrames

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                if (absPos >= gateEndPos) {
                    if (!releaseStarted) {
                        releaseStartLevel = currentLevel
                        releaseStarted = true
                    }
                    val relPos = absPos - gateEndPos
                    val p = ((relPos + relOffset) / relDenom).coerceAtMost(1.0)
                    val omp = 1.0 - p
                    val shape = adsrCurveShape(relCurve, omp, expKVal, expNorm)
                    currentLevel = releaseStartLevel * shape
                } else {
                    releaseStarted = false
                    currentLevel = when {
                        absPos < attackFrames -> {
                            val p = absPos * attRate
                            adsrCurveShape(attCurve, p, expKVal, expNorm)
                        }

                        absPos < attDecFrames -> {
                            val decPos = absPos - attackFrames
                            val p = decPos * decRate
                            val omp = 1.0 - p
                            val shape = adsrCurveShape(decCurve, omp, expKVal, expNorm)
                            sustainLevelVal + (1.0 - sustainLevelVal) * shape
                        }
                        else -> sustainLevelVal
                    }
                }

                if (currentLevel < 0.0) currentLevel = 0.0

                // Opt-in de-click: one-pole low-pass on the gain, primed to the first rendered
                // level so always-on voices / mid-phase block starts don't fade in.
                val gain = if (declickOn) {
                    if (!smoothPrimed) {
                        smoothedLevel = currentLevel
                        smoothPrimed = true
                    } else {
                        smoothedLevel += declickCoeff * (currentLevel - smoothedLevel)
                    }
                    smoothedLevel
                } else {
                    currentLevel
                }

                buffer[i] = (input[i] * gain)
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
    attackCurve: AdsrCurve = AdsrCurve.Default,
    decayCurve: AdsrCurve = AdsrCurve.Default,
    releaseCurve: AdsrCurve = AdsrCurve.Default,
    declickSeconds: Double = 0.0,
): Ignitor = adsr(
    ParamIgnitor("attackSec", attackSec),
    ParamIgnitor("decaySec", decaySec),
    ParamIgnitor("sustainLevel", sustainLevel),
    ParamIgnitor("releaseSec", releaseSec),
    attackCurve, decayCurve, releaseCurve,
    ParamIgnitor("declickSeconds", declickSeconds),
)
