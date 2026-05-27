package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Shared control-rate envelope calculation for filter modulation and FM depth.
 *
 * Calculates a single envelope value (0.0–1.0) at the given block position.
 * Per-stage shape curves (Linear/Square/Cube) are read from [Voice.Envelope].
 * Uses the fixed release calculation: decays from the actual level at gate end,
 * not from sustainLevel.
 *
 * All arithmetic uses Int/Double — no Long boxing on Kotlin/JS.
 */
fun calculateControlRateEnvelope(
    env: Voice.Envelope,
    blockStart: Int,
    startFrame: Int,
    gateEndFrame: Int,
): Double {
    val currentFrame = maxOf(blockStart, startFrame)
    val absPos = currentFrame - startFrame
    val gateEndPos = gateEndFrame - startFrame

    val envValue = if (absPos >= gateEndPos) {
        val levelAtGateEnd = envelopeLevelAtPosition(env, gateEndPos)
        val relPos = absPos - gateEndPos
        val relDenom = if (env.releaseFrames > 0) env.releaseFrames else 1.0
        val p = (relPos / relDenom).coerceAtMost(1.0)
        val omp = 1.0 - p
        val shape = when (env.releaseCurve) {
            AdsrCurve.Linear -> omp
            AdsrCurve.Square -> omp * omp
            AdsrCurve.Cube -> omp * omp * omp
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
        }
        env.sustainLevel + (1.0 - env.sustainLevel) * shape
    }

    else -> env.sustainLevel
}
