/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Fluent config methods for the sawtooth oscillator (`Osc.saw()`). Each returns a new
 * [IgnitorDsl.Sawtooth], so they chain — put these *before* the base wrappers (`.lowpass()`/`.adsr()`),
 * which return the base [IgnitorDsl] and so come last (config-first ordering).
 *
 * `freq`/`analog` accept an [IgnitorDslLike] (a number → [IgnitorDsl.Constant], or an `Osc.*` graph for
 * audio-rate modulation). The shape knobs (`resetSamples`/`shapeMax`) are plain scalars — they mirror the
 * `SAW_RESET_SAMPLES` / `SAW_SHAPE_MAX` engine constants.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Sawtooth::class)
object KlangScriptSawtoothExtensions {

    /** Oscillator frequency. Omit to track the playing note's pitch (the default). */
    @KlangScript.Method
    fun freq(self: IgnitorDsl.Sawtooth, freq: IgnitorDslLike): IgnitorDsl.Sawtooth =
        self.copy(freq = freq.toIgnitorDsl())

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.Sawtooth, analog: IgnitorDslLike): IgnitorDsl.Sawtooth =
        self.copy(analog = analog.toIgnitorDsl())

    /** Analog flyback time in samples: lower = brighter/sharper reset, higher = softer (default 2.0). */
    @KlangScript.Method
    fun resetSamples(self: IgnitorDsl.Sawtooth, resetSamples: Double): IgnitorDsl.Sawtooth =
        self.copy(resetSamples = resetSamples)

    /** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane (default 0.5). */
    @KlangScript.Method
    fun shapeMax(self: IgnitorDsl.Sawtooth, shapeMax: Double): IgnitorDsl.Sawtooth =
        self.copy(shapeMax = shapeMax)
}
