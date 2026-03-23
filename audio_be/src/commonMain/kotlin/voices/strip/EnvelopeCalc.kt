package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * Shared control-rate envelope calculation for filter modulation and FM depth.
 *
 * Calculates a single envelope value (0.0–1.0) at the given block position.
 * Uses the fixed release calculation: decays from the actual level at gate end,
 * not from sustainLevel.
 */
fun calculateControlRateEnvelope(
    env: Voice.Envelope,
    blockStart: Long,
    startFrame: Long,
    gateEndFrame: Long,
): Double {
    val currentFrame = maxOf(blockStart, startFrame)
    val absPos = currentFrame - startFrame
    val gateEndPos = gateEndFrame - startFrame

    val envValue = if (absPos >= gateEndPos) {
        val levelAtGateEnd = envelopeLevelAtPosition(env, gateEndPos)
        val relPos = absPos - gateEndPos
        val relRate = if (env.releaseFrames > 0) levelAtGateEnd / env.releaseFrames else 1.0
        levelAtGateEnd - (relPos * relRate)
    } else {
        envelopeLevelAtPosition(env, absPos)
    }

    return envValue.coerceIn(0.0, 1.0)
}

/** Calculate the envelope level at a given position (attack/decay/sustain only). */
fun envelopeLevelAtPosition(env: Voice.Envelope, absPos: Long): Double = when {
    absPos < env.attackFrames -> {
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        absPos * attRate
    }

    absPos < env.attackFrames + env.decayFrames -> {
        val decPos = absPos - env.attackFrames
        val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
        1.0 - (decPos * decRate)
    }

    else -> env.sustainLevel
}
