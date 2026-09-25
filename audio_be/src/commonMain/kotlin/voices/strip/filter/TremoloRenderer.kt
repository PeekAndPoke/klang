/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.TremoloCore
import io.peekandpoke.klang.audio_be.parseLfoShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Tremolo effect — rhythmic amplitude modulation from a per-voice LFO.
 *
 * The LFO is an `LfoShape` evaluated at a phase that advances every sample, seeded by
 * [startPhase] and warped by [skew]. At the neutral settings ([skew] 0.0, [startPhase] 0.0,
 * [shape] null or `"sine"`) the output is the shipped sine tremolo to the polynomial sine's
 * bound (`FAST_SIN_MAX_ERROR`), and every spelling of that neutral sine renders the same
 * samples: see the fast path in `lfoNorm`.
 *
 * The law itself is [TremoloCore], the ONE copy the Ignitor `Tremolo` node renders through too
 * (phase 3 step 3b, 2026-09-25); this class only adapts the strip's contract: every knob is read
 * once, at construction, and the block is rendered in place. [rate] arrives in Hz and
 * [startPhase] in cycles (`0..1` = one full LFO cycle, the sprudel `tremolo(phase)` unit); the
 * core converts both.
 *
 * NO parameter has a default, deliberately. This renderer spent its whole life dropping
 * skew/phase/shape on the floor because the one call site passed rate and depth only (ledger
 * W10) — a default would let exactly that happen again in silence.
 */
class TremoloRenderer(
    rate: Double,
    private val depth: Double,
    skew: Double,
    startPhase: Double,
    shape: String?,
    sampleRate: Int,
) : BlockRenderer {

    private val core = TremoloCore(parseLfoShape(shape), startPhase, skew)

    private val phaseIncrement: Double = TremoloCore.increment(rate, sampleRate)

    override fun render(ctx: BlockContext) {
        // UNREACHABLE by construction: FilterPipelineBuilder only builds this stage when
        // `depth > 0.0`, and depth is a constructor val fixed for the note's life (this door
        // samples its controls once per note). It is a guard, not a bypass — which is why it
        // does NOT advance the clock the way the ignitor door's tremolo does (`TremoloCore.skip`).
        // If depth ever becomes per-block here, this arm needs that advance: a bypass that stops
        // the clock is ledger W2 exactly.
        if (depth <= 0.0) {
            return
        }

        // `ctx.offset` is a var on a shared, escaping object; read the window once.
        val buf = ctx.audioBuffer

        core.apply(buf, buf, ctx.offset, ctx.windowEnd, phaseIncrement, depth)
    }
}
