/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope

/**
 * Updates filter cutoff frequencies from envelope modulation and (optionally)
 * per-voice slow OU drift. Runs at control rate (once per block) for efficiency.
 *
 * Per block: `newCutoff = baseCutoff × (1 + depth × envValue) × driftMul`
 * where `driftMul` is `1.0` when the filter has no drift attached, otherwise
 * the next sample of the per-voice [AnalogDrift] (advanced once per block, so
 * the drift's effective time constants are scaled by `sampleRate / blockFrames`).
 */
class FilterModRenderer(
    private val modulators: List<Voice.FilterModulator>,
    private val startFrame: Int,
    private val gateEndFrame: Int,
) : BlockRenderer {
    override fun render(ctx: BlockContext) {
        for (mod in modulators) {
            val envValue = calculateControlRateEnvelope(mod.envelope, ctx.blockStart, startFrame, gateEndFrame)
            val drift = mod.drift
            val driftMul = if (drift != null && drift.active) drift.nextMultiplier() else 1.0
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue) * driftMul
            mod.filter.setCutoff(newCutoff)
        }
    }
}
