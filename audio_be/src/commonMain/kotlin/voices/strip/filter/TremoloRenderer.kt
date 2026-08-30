/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.LfoShape
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.lfoDutyOf
import io.peekandpoke.klang.audio_be.lfoNorm
import io.peekandpoke.klang.audio_be.lfoScaleFirst
import io.peekandpoke.klang.audio_be.lfoScaleSecond
import io.peekandpoke.klang.audio_be.parseLfoShape
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Tremolo effect — rhythmic amplitude modulation from a per-voice LFO.
 *
 * The LFO is an [LfoShape] evaluated at a phase that advances every sample, seeded by
 * [startPhase] and warped by [skew]. At the neutral settings ([skew] 0.0, [startPhase] 0.0,
 * [shape] null or `"sine"`) the output is the shipped sine tremolo BIT for bit — see the fast
 * path in [lfoNorm], which is what makes that guarantee rather than approximates it.
 *
 * Unit conversions live HERE, once (parameter parity: one conversion site per quantity).
 * [rate] arrives in Hz and [startPhase] in cycles (`0..1` = one full LFO cycle, the sprudel
 * `tremolophase` unit); both become radians at construction.
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

    private val lfoShape: LfoShape = parseLfoShape(shape)

    /** Cycle position the waveform's two halves are split at; [LfoShape] owns the skew law. */
    private val duty: Double = lfoDutyOf(skew, lfoShape)

    private val scaleFirst: Double = lfoScaleFirst(duty)

    private val scaleSecond: Double = lfoScaleSecond(duty)

    /**
     * Seeded from the authored cycle offset, so the LFO starts at that position in its own
     * cycle (the midpoint rising for sine and triangle, the top for square and ramp, the
     * bottom for sawtooth). `wrapPhase` does the whole job: it folds an out-of-range seed
     * (`tremolophase(3.25)` is a quarter cycle) and returns 0.0 for a non-finite one, so a
     * hostile pattern value cannot poison the accumulator.
     */
    private var phase: Double = (startPhase * TWO_PI).wrapPhase(TWO_PI)

    private val phaseIncrement: Double = (rate * TWO_PI) / sampleRate

    override fun render(ctx: BlockContext) {
        // UNREACHABLE by construction: FilterPipelineBuilder only builds this stage when
        // `depth > 0.0`, and depth is a constructor val fixed for the note's life (this door
        // samples its controls once per note). It is a guard, not a bypass — which is why it
        // does NOT bulk-advance the phase the way the ignitor door's tremolo does. If depth
        // ever becomes per-block here, this arm needs that advance: a bypass that stops the
        // clock is ledger W2 exactly.
        if (depth <= 0.0) {
            return
        }

        // Everything the LFO needs is loop-invariant except the phase — hoisted out of the
        // per-sample path, and the window walked the way the sibling DistortionRenderer walks
        // it: `ctx.offset` is a var on a shared, escaping object, so an `offset + i` inside
        // the loop is a field read neither the JIT nor Kotlin/JS can hoist for us.
        val buf = ctx.audioBuffer
        val amount = depth
        val increment = phaseIncrement
        val shape = lfoShape
        val splitAt = duty
        val first = scaleFirst
        val second = scaleSecond
        var p = phase

        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            // wrapPhase over the bare subtract (ledger W2): identical in range; a non-finite
            // or negative rate can no longer kill the phase for the voice's life.
            p = (p + increment).wrapPhase(TWO_PI)

            val level = lfoNorm(shape, p, splitAt, first, second)
            val gain = 1.0 - (amount * (1.0 - level))

            buf[i] = (buf[i] * gain)
        }

        phase = p
    }
}
