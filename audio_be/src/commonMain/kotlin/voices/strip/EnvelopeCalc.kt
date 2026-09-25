/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.voices.Voice

/**
 * The voice strip's control-rate FM envelope: one value per block, 0.0 to 1.0, from [EnvelopeCore], the
 * engine's one envelope law. [core] is the calling renderer's own evaluator (prepared here).
 *
 * The value is taken at the block's first rendered frame (the voice's onset on its first block) and
 * HELD for the block: the strip FM holds its depth flat (block-framing ledger E11, recorded and
 * deliberately not fixed piecemeal). The strip filter prepares its envelope the same way
 * ([prepareControlRateEnvelope]) and reads it at the block's two ends instead (`FilterModRenderer`).
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
    core.prepareControlRateEnvelope(env, startFrame, gateEndFrame)

    return core.at(controlRatePos(blockStart, startFrame)).coerceIn(0.0, 1.0)
}

/**
 * The voice-relative frame of a block's first rendered frame: the voice's onset on its first block.
 * `maxOf` is the same expression `Voice.render` derives the block's offset from, so this lands on the
 * onset of a voice that starts mid-block.
 */
internal fun controlRatePos(blockStart: Double, startFrame: Double): Int = (maxOf(blockStart, startFrame) - startFrame).toInt()

/** Prepares this [EnvelopeCore] for one block of a strip modulation envelope ([env] counts frames). */
internal fun EnvelopeCore.prepareControlRateEnvelope(env: Voice.Envelope, startFrame: Double, gateEndFrame: Double) {
    prepare(
        attackFrames = env.attackFrames,
        decayFrames = env.decayFrames,
        sustainLevel = env.sustainLevel,
        releaseFrames = env.releaseFrames,
        gateEndPos = (gateEndFrame - startFrame).toInt(),
        attackCurve = env.attackCurve,
        decayCurve = env.decayCurve,
        releaseCurve = env.releaseCurve,
    )
}
