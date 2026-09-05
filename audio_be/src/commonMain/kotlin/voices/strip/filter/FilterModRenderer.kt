/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import kotlin.math.pow

/**
 * Updates filter cutoff frequencies from envelope modulation and (optionally)
 * per-voice slow OU drift. Runs at control rate (once per block) for efficiency.
 *
 * Per block: `newCutoff = baseCutoff × 2^(depth/12 × envValue) × driftMul` (depth in semitones)
 * where `driftMul` is `1.0` when the filter has no drift attached, otherwise
 * the next sample of the per-voice [AnalogDrift] (advanced once per block, so
 * the drift's effective time constants are scaled by `sampleRate / blockFrames`).
 */
class FilterModRenderer(
    private val modulators: List<Voice.FilterModulator>,
        // Absolute backend frame — Double, see RenderClock.cursorFrame.
    private val startFrame: Double,
) : BlockRenderer {
    override fun render(ctx: BlockContext) {
        for (mod in modulators) {
            // Gate read from the ctx per call — a realtime note-off may move it (Voice.releaseGate)
            val envValue = calculateControlRateEnvelope(mod.envelope, ctx.blockStart, startFrame, ctx.gateEndFrame)
            val drift = mod.drift
            val driftMul = if (drift != null && drift.active) drift.nextMultiplier() else 1.0
            // C3 (filter unification): depth is SEMITONES (cutoff = base * 2^(depth/12 * env)).
            val newCutoff = mod.baseCutoff * 2.0.pow(mod.depth / 12.0 * envValue) * driftMul
            mod.filter.setCutoff(newCutoff)
        }
    }
}
