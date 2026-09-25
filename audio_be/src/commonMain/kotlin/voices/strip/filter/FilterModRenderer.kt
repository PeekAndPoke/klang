/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_be.filters.filterEnvCutoff
import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.controlRatePos
import io.peekandpoke.klang.audio_be.voices.strip.prepareControlRateEnvelope

/**
 * Moves the strip filters' cutoffs from their envelopes and (optionally) the per-voice slow OU drift,
 * once per block, by the Ignitor filter node's law (decision D3, the sampling): the cutoff is taken at
 * the block's first rendered frame and at the frame after its last, `baseCutoff × 2^(depth/12 × env) ×
 * driftMul` at each (depth in semitones), and the filter sweeps its coefficients linearly between the
 * two across the block (`AudioFilter.Tunable.sweepCutoff`, [filterEnvCutoff] and `SvfCoeffSweep`, the
 * node's own code).
 *
 * `driftMul` is `1.0` when the filter has no drift attached, otherwise the next sample of the
 * per-voice [AnalogDrift], advanced once per block (so the drift's effective time constants are scaled
 * by `sampleRate / blockFrames`) and HELD across the block: both ends carry it, and it changes at the
 * block boundary, as on the node. A modulator with depth 0 (drift only) never reads its envelope; its
 * sweep has equal ends, a snap per block.
 */
class FilterModRenderer(
    private val modulators: List<Voice.FilterModulator>,
        // Absolute backend frame — Double, see RenderClock.cursorFrame.
    private val startFrame: Double,
) : BlockRenderer {
    private val core = EnvelopeCore()

    override fun render(ctx: BlockContext) {
        val pos = controlRatePos(ctx.blockStart, startFrame)
        val frames = ctx.length

        for (mod in modulators) {
            val drift = mod.drift
            val driftMul = if (drift != null && drift.active) drift.nextMultiplier() else 1.0

            if (mod.depth == 0.0) {
                val cutoff = mod.baseCutoff * driftMul

                mod.filter.sweepCutoff(cutoff, cutoff, frames)
            } else {
                // Gate read from the ctx per call: a realtime note-off may move it (Voice.releaseGate)
                core.prepareControlRateEnvelope(mod.envelope, startFrame, ctx.gateEndFrame)

                val cutoffStart = core.filterEnvCutoff(pos, mod.baseCutoff, mod.depth) * driftMul
                val cutoffEnd = core.filterEnvCutoff(pos + frames, mod.baseCutoff, mod.depth) * driftMul

                mod.filter.sweepCutoff(cutoffStart, cutoffEnd, frames)
            }
        }
    }
}
