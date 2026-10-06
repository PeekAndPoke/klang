/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.wrapToUnitCycle

/**
 * A periodic oscillator's `phase` input (`docs/tasks/oscillator-phase-knob.md`): an offset in cycles, wrapped into
 * `[0, 1)`, that the oscillator adds to its running phase every sample. The oscillator reads its shape at
 * `accumulator + offset`. Two paths, chosen once from the input's structure ([Ignitor.isBlockConstant]):
 *
 * - **Block-constant** (a number, a slot, arithmetic over them): the offset can only change at a block boundary, so
 *   the oscillator moves its ACCUMULATOR by the change once per block ([blockDelta]) and its per-sample loop stays
 *   exactly as it was. An unchanged offset is a delta of 0 and the oscillator touches nothing, so a phase of 0
 *   renders bit for bit what an oscillator without the input renders. A constant is therefore the start phase.
 * - **Signal** ([isSignal]): the input renders a block ([render]) and the oscillator's phased loop reads the shape
 *   at `accumulator + offset[i]` per sample, leaving the accumulator itself untouched: phase modulation.
 *
 * The oscillators get no instance when the authored phase is the literal 0 (the default), so the default costs one
 * null check per block. A jumping offset clicks: raw by design. A fast-moving offset also adds to the instantaneous
 * frequency, so it squeezes the soft edges of the saw and square family (sized from the note's own increment), which
 * then alias as their raw twins do.
 *
 * A block-constant input that answers no value for a block (a breach of [Ignitor.controlRateValueOrNull]'s contract)
 * is not dropped: [blockDelta] unfolds the offset applied so far and the block renders through the phased loop, as
 * the pulse oscillator's `duty` falls through to its per-sample path.
 */
internal class PhaseOffset(private val input: Ignitor) {

    /** True when the input moves within a block, structural and fixed for the note. */
    val isSignal: Boolean = !input.isBlockConstant

    /** True when THIS block reads the offset per sample: always for a signal, for a block-constant input only on a breach. */
    var perSample: Boolean = isSignal
        private set

    /** The wrapped offset already folded into the accumulator (block-constant path), in cycles. */
    var applied: Double = 0.0
        private set

    /**
     * Block-constant path: the change of the wrapped offset since the last block, in cycles, in `(-1, 1)`, and 0.0
     * when nothing changed. A non-finite value reads as 0. On a contract breach (no value) it returns the change that
     * takes the applied offset back out and sets [perSample] for this block.
     */
    fun blockDelta(freqHz: Double): Double {
        val value = input.controlRateValueOrNull(freqHz)

        perSample = value == null

        val o = value?.wrapToUnitCycle() ?: 0.0
        val d = o - applied

        applied = o

        return d
    }

    /** Signal path: renders this block's offsets (raw, not yet wrapped) into [buffer]. */
    fun render(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        input.generate(buffer, freqHz, ctx)
    }
}
