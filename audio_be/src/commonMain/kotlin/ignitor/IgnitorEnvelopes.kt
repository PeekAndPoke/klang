/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.EnvelopeDeclick
import io.peekandpoke.klang.audio_be.envDeclickCoeff
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
 * - Release: ramps from the level at the gate to 0.0 over [releaseSec], shape via [releaseCurve]
 *
 * The level is [EnvelopeCore]'s, the engine's one envelope law (fractional attack and decay frames,
 * the release on `floor(N)` frames, the sustain raw); this host floors it at 0 (a negative gain would
 * invert the signal) and runs the optional de-click ([EnvelopeDeclick]).
 *
 * Voice timing (gate end, release duration) is read from [IgniteContext].
 * The envelope does NOT control voice lifecycle — that's handled by frame counting in Voice.
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
 * ON (as the strip's VCA was on every voice, and a built-in's classic envelope is since phase 3 step 6), so an authored `Constant(NaN)` or a slot whose
 * default is the sentinel arrives here intact. One knob then carries it into the samples:
 * [AdsrIgnitor.sustainLevel], because the sustain is raw (no clamp since the envelope law, phase 3
 * D3) and the level multiplies every sample. (`expK` was the second until phase 3 step 3c removed
 * that knob; every exponential stage now bends at [ADSR_EXP_K].)
 *
 * An INFINITY is non-finite too and takes the default: `+Inf` and `-Inf` both sustain at
 * [ADSR_SUSTAIN_LEVEL]. That is the house rule, not a slip: a non-finite value reads as UNSET
 * (`/dsl-design` section 4), which is exactly how the build-time gate one file over reads every knob
 * it tests, and an unset knob takes its default rather than a rail. `IgnitorGateSpec` pins both
 * infinities.
 *
 * The other knobs need nothing: the envelope law reads a NaN or negative time as a zero-length
 * stage; `declickSeconds` is read through `> 0.0`, which a NaN fails, so a non-finite de-click is
 * off. `releaseSec`'s other half, the voice's release TAIL,
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
    private val core = EnvelopeCore()
    private val declick = EnvelopeDeclick()

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { input ->
            upstream.generate(input, freqHz, ctx)

            val attackSecVal = Ignitors.readParam(attackSec, freqHz, ctx)
            val decaySecVal = Ignitors.readParam(decaySec, freqHz, ctx)
            // NaN-guard: a non-finite sustain reads as UNSET and takes the node's default (see the note
            // on `finiteOr`). No clamp: every finite sustain passes raw (the Motor stays raw); the
            // amplitude floor at 0 below is the only bound.
            val sustainLevelVal = finiteOr(Ignitors.readParam(sustainLevel, freqHz, ctx), ADSR_SUSTAIN_LEVEL)
            val releaseSecVal = Ignitors.readParam(releaseSec, freqHz, ctx)

            // declick is a control-rate slot: read per block, derive its coefficient once here.
            val declickSecondsVal = Ignitors.readParam(declickSeconds, freqHz, ctx)
            val declickOn = declickSecondsVal > 0.0
            val declickCoeff = if (declickOn) envDeclickCoeff(declickSecondsVal, ctx.sampleRateD) else 0.0

            val gateEndPos = ctx.gateEndFrame

            // Every exponential stage bends at ADSR_EXP_K (the per-envelope knob was removed in 3c).
            core.prepare(
                attackFrames = attackSecVal * ctx.sampleRate,
                decayFrames = decaySecVal * ctx.sampleRate,
                sustainLevel = sustainLevelVal,
                releaseFrames = releaseSecVal * ctx.sampleRate,
                gateEndPos = gateEndPos,
                attackCurve = attackCurve,
                decayCurve = decayCurve,
                releaseCurve = releaseCurve,
            )

            var absPos = ctx.voiceElapsedFrames

            val end = ctx.windowEnd
            for (i in ctx.offset until end) {
                // The amplitude floors at 0: a negative gain would invert the signal.
                val level = core.at(absPos)
                val currentLevel = if (level < 0.0) 0.0 else level

                // Opt-in de-click: one-pole low-pass on the gain, primed to the first rendered
                // level so always-on voices / mid-phase block starts don't fade in.
                val gain = if (declickOn) declick.next(currentLevel, declickCoeff) else currentLevel

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
