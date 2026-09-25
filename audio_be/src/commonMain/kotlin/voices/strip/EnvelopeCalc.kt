/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * The voice strip's control-rate envelope for filter modulation and FM depth: one value per block, 0.0
 * to 1.0, from [EnvelopeCore], the engine's one envelope law. [core] is the calling renderer's own
 * evaluator (prepared here, so one instance serves every modulator of a renderer in turn).
 *
 * The value is taken at the block's first rendered frame (the voice's onset on its first block) and
 * HELD for the block: the strip filter then ramps its coefficients (`BaseSvf.setCutoff`), the strip FM
 * holds its depth flat (block-framing ledger E11, recorded and deliberately not fixed piecemeal).
 *
 * All arithmetic uses Int/Double, no Long boxing on Kotlin/JS.
 */
internal fun calculateControlRateEnvelope(
    env: Voice.Envelope,
    // Absolute backend frames are Double (see RenderClock.cursorFrame); the relative positions
    // derived from them below are Int, and everything downstream of that stays Int.
    blockStart: Double,
    startFrame: Double,
    gateEndFrame: Double,
    core: EnvelopeCore,
): Double {
    val currentFrame = maxOf(blockStart, startFrame)
    val absPos = (currentFrame - startFrame).toInt()
    val gateEndPos = (gateEndFrame - startFrame).toInt()

    core.prepare(
        attackFrames = env.attackFrames,
        decayFrames = env.decayFrames,
        sustainLevel = env.sustainLevel,
        releaseFrames = env.releaseFrames,
        gateEndPos = gateEndPos,
        attackCurve = env.attackCurve,
        decayCurve = env.decayCurve,
        releaseCurve = env.releaseCurve,
    )

    return core.at(absPos).coerceIn(0.0, 1.0)
}
