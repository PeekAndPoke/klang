/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.stdlib.KlangScriptSuperRampExtensions.gainJitter

/**
 * Fluent config methods for the superramp oscillator (`Osc.superramp()`). Each returns a new
 * [IgnitorDsl.SuperRamp], so they chain — put these *before* the base wrappers (`.lowpass()`/`.adsr()`),
 * which return the base [IgnitorDsl] and so come last (config-first ordering).
 *
 * `voices`/`spread`/`analog`/`freq` accept an [IgnitorDslLike] (a number → [IgnitorDsl.Constant], or an
 * `Osc.*` graph for audio-rate modulation). The character knobs (`spreadPower`/`sideAtten`/`gainJitter`/
 * `centerJitter`) are plain scalars read once per voice — they mirror the `SUPERRAMP_*` engine constants.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.SuperRamp::class)
object KlangScriptSuperRampExtensions {

    /** Oscillator frequency. Omit to track the playing note's pitch (the default). */
    @KlangScript.Method
    fun freq(self: IgnitorDsl.SuperRamp, freq: IgnitorDslLike): IgnitorDsl.SuperRamp =
        self.copy(freq = freq.toIgnitorDsl())

    /** Number of detuned voices in the stack (default 8). */
    @KlangScript.Method
    fun voices(self: IgnitorDsl.SuperRamp, voices: IgnitorDslLike): IgnitorDsl.SuperRamp =
        self.copy(voices = voices.toIgnitorDsl())

    /** Unison frequency spread between the voices (default 0.2). Same as the pattern-level `.spread()`. */
    @KlangScript.Method
    fun spread(self: IgnitorDsl.SuperRamp, spread: IgnitorDslLike): IgnitorDsl.SuperRamp =
        self.copy(spread = spread.toIgnitorDsl())

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.SuperRamp, analog: IgnitorDslLike): IgnitorDsl.SuperRamp =
        self.copy(analog = analog.toIgnitorDsl())

    /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward (default 1.2). */
    @KlangScript.Method
    fun spreadPower(self: IgnitorDsl.SuperRamp, spreadPower: Double): IgnitorDsl.SuperRamp =
        self.copy(spreadPower = spreadPower)

    /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice (default 0.1). */
    @KlangScript.Method
    fun sideAtten(self: IgnitorDsl.SuperRamp, sideAtten: Double): IgnitorDsl.SuperRamp =
        self.copy(sideAtten = sideAtten)

    /** Per-voice random amplitude offset (±fraction); 0 = off (default 0.15). */
    @KlangScript.Method
    fun gainJitter(self: IgnitorDsl.SuperRamp, gainJitter: Double): IgnitorDsl.SuperRamp =
        self.copy(gainJitter = gainJitter)

    /** Fraction of [gainJitter] the on-pitch center voice gets: 0 = stable center, 1 = jittered like sides (default 0.4). */
    @KlangScript.Method
    fun centerJitter(self: IgnitorDsl.SuperRamp, centerJitter: Double): IgnitorDsl.SuperRamp =
        self.copy(centerJitterScale = centerJitter)
}
