/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_be.releaseProgressDenom
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Shared control-rate envelope calculation for filter modulation and FM depth.
 *
 * Calculates a single envelope value (0.0–1.0) at the given block position.
 * Per-stage shape curves (Linear/Square/Cube/SCurve/InvSquare/Exponential; default exp) are read from [Voice.Envelope].
 * Uses the fixed release calculation: decays from the actual level at gate end,
 * not from sustainLevel.
 *
 * All arithmetic uses Int/Double — no Long boxing on Kotlin/JS.
 */
fun calculateControlRateEnvelope(
    env: Voice.Envelope,
    // Absolute backend frames are Double (see RenderClock.cursorFrame); the relative positions
    // derived from them below are Int, and everything downstream of that stays Int.
    blockStart: Double,
    startFrame: Double,
    gateEndFrame: Double,
): Double {
    val currentFrame = maxOf(blockStart, startFrame)
    val absPos = (currentFrame - startFrame).toInt()
    val gateEndPos = (gateEndFrame - startFrame).toInt()

    val envValue = if (absPos >= gateEndPos) {
        val levelAtGateEnd = envelopeLevelAtPosition(env, gateEndPos)
        val relPos = absPos - gateEndPos
        // The split between the two helpers is by DESTINATION, not by evaluator:
        //  - the DENOMINATOR is unified everywhere a curve is evaluated, because it is a time-base
        //    correction (a release of N frames spans relPos 0..N-1) and applies whatever the value
        //    drives. `IgnitorFilters.computeFilterEnvelope` is exempt only because it is a straight
        //    LINEAR ramp with no curve endpoint to land on. Note this site does NOT floor
        //    releaseFrames the way EnvelopeRenderer does: this envelope's release is the FILTER's,
        //    independent of the voice's rendered span, so there is no last-rendered-frame for it to
        //    land on and nothing to floor against.
        //  - the OFFSET is amplitude-only. It exists to stop a step when a release is too short to
        //    ramp, and a step matters for a gain, not for a cutoff or an FM depth. VoiceFactory
        //    always builds the FM envelope with releaseFrames = 0, so applying it here would drop
        //    FM depth to zero at gate end for every FM voice in every song.
        // `EnvelopeCalcNoOffsetSpec` guards that second bullet.
        val p = (relPos / releaseProgressDenom(env.releaseFrames)).coerceAtMost(1.0)
        val omp = 1.0 - p
        val shape = when (env.releaseCurve) {
            AdsrCurve.Linear -> omp
            AdsrCurve.Square -> omp * omp
            AdsrCurve.Cube -> omp * omp * omp
            AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
            AdsrCurve.InvSquare -> omp * (2.0 - omp)
            AdsrCurve.Exponential -> adsrExpShape(omp)
        }
        levelAtGateEnd * shape
    } else {
        envelopeLevelAtPosition(env, absPos)
    }

    return envValue.coerceIn(0.0, 1.0)
}

/** Calculate the envelope level at a given position (attack/decay/sustain only). */
fun envelopeLevelAtPosition(env: Voice.Envelope, absPos: Int): Double = when {
    absPos < env.attackFrames -> {
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        val p = absPos * attRate
        when (env.attackCurve) {
            AdsrCurve.Linear -> p
            AdsrCurve.Square -> p * p
            AdsrCurve.Cube -> p * p * p
            AdsrCurve.SCurve -> if (p < 0.5) 2.0 * p * p else 1.0 - 2.0 * (1.0 - p) * (1.0 - p)
            AdsrCurve.InvSquare -> p * (2.0 - p)
            AdsrCurve.Exponential -> adsrExpShape(p)
        }
    }

    absPos < env.attackFrames + env.decayFrames -> {
        val decPos = absPos - env.attackFrames
        val decRate = if (env.decayFrames > 0) 1.0 / env.decayFrames else 1.0
        val p = decPos * decRate
        val omp = 1.0 - p
        val shape = when (env.decayCurve) {
            AdsrCurve.Linear -> omp
            AdsrCurve.Square -> omp * omp
            AdsrCurve.Cube -> omp * omp * omp
            AdsrCurve.SCurve -> if (omp < 0.5) 2.0 * omp * omp else 1.0 - 2.0 * (1.0 - omp) * (1.0 - omp)
            AdsrCurve.InvSquare -> omp * (2.0 - omp)
            AdsrCurve.Exponential -> adsrExpShape(omp)
        }
        env.sustainLevel + (1.0 - env.sustainLevel) * shape
    }

    else -> env.sustainLevel
}
