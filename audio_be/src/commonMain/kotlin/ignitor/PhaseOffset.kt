/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.wrapToUnitCycle

/**
 * A periodic oscillator's `phase` input (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`): an offset in cycles, wrapped into
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
 */
internal class PhaseOffset(private val input: Ignitor) {

    /** True when the input moves within a block, structural and fixed for the note: the block then reads it per sample. */
    val isSignal: Boolean = !input.isBlockConstant

    /** The wrapped offset already folded into the accumulator (block-constant path), in cycles. */
    var applied: Double = 0.0
        private set

    /**
     * Block-constant path: the change of the wrapped offset since the last block, in cycles, in `(-1, 1)`, and 0.0
     * when nothing changed. A non-finite value reads as 0. Called only when not [isSignal], so the input's scalar is
     * its value (the "no value despite the flag" breach this used to unfold is gone with the nullable scalar,
     * maintainer, 2026-10-10).
     */
    fun blockDelta(freqHz: Double): Double {
        val o = input.controlRateValue(freqHz).wrapToUnitCycle()
        val d = o - applied

        applied = o

        return d
    }

    /** Signal path: renders this block's offsets (raw, not yet wrapped) into [buffer]. */
    fun render(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        input.generate(buffer, freqHz, ctx)
    }
}
