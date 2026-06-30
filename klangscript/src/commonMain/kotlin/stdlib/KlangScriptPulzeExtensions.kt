/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Fluent config methods for the pulse oscillator (`Osc.square()` → [IgnitorDsl.Pulze]). Each returns a new
 * [IgnitorDsl.Pulze], so they chain — put these *before* the base wrappers (`.lowpass()`/`.adsr()`), which
 * return the base [IgnitorDsl] and so come last (config-first ordering).
 *
 * `freq`/`duty`/`analog` accept an [IgnitorDslLike] (a number → [IgnitorDsl.Constant], or an `Osc.*` graph
 * for audio-rate modulation — `duty` is PWM-capable). The edge knobs (`flankSamples`/`riseFlank`/`fallFlank`)
 * are plain scalars — they mirror the `PULSE_*` engine constants.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Pulze::class)
object KlangScriptPulzeExtensions {

    /** Oscillator frequency. Omit to track the playing note's pitch (the default). */
    @KlangScript.Method
    fun freq(self: IgnitorDsl.Pulze, freq: IgnitorDslLike): IgnitorDsl.Pulze =
        self.copy(freq = freq.toIgnitorDsl())

    /** Pulse width / duty cycle (0..1, default 0.5 = square). Accepts an `Osc.*` graph for PWM. */
    @KlangScript.Method
    fun duty(self: IgnitorDsl.Pulze, duty: IgnitorDslLike): IgnitorDsl.Pulze =
        self.copy(duty = duty.toIgnitorDsl())

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.Pulze, analog: IgnitorDslLike): IgnitorDsl.Pulze =
        self.copy(analog = analog.toIgnitorDsl())

    /** Minimum flank length in samples (a floor on every edge → softens with pitch; default 2.0). */
    @KlangScript.Method
    fun flankSamples(self: IgnitorDsl.Pulze, flankSamples: Double): IgnitorDsl.Pulze =
        self.copy(flankSamples = flankSamples)

    /** Rising-edge flank fraction of the plateau: 0 = sharpest (min floor), 1 = full ramp (default 0.0). */
    @KlangScript.Method
    fun riseFlank(self: IgnitorDsl.Pulze, riseFlank: Double): IgnitorDsl.Pulze =
        self.copy(riseFlank = riseFlank)

    /** Falling-edge flank fraction of the plateau: 0 = sharpest (min floor), 1 = full ramp (default 0.0). */
    @KlangScript.Method
    fun fallFlank(self: IgnitorDsl.Pulze, fallFlank: Double): IgnitorDsl.Pulze =
        self.copy(fallFlank = fallFlank)
}
