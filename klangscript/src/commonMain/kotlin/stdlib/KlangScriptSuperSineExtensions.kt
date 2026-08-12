/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.stdlib.KlangScriptSuperSineExtensions.gainJitter

/**
 * Fluent config methods for the supersine oscillator (`Osc.supersine()`). Each returns a new
 * [IgnitorDsl.SuperSine], so they chain — put these *before* the base wrappers (`.lowpass()`/`.adsr()`),
 * which return the base [IgnitorDsl] and so come last (config-first ordering).
 *
 * `voices`/`spread`/`analog`/`freq` accept an [IgnitorDslLike] (a number → [IgnitorDsl.Constant], or an
 * `Osc.*` graph for audio-rate modulation). The character knobs (`spreadPower`/`sideAtten`/`gainJitter`/
 * `centerJitter`) are plain scalars read once per voice — they mirror the `SUPERSINE_*` engine constants.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.SuperSine::class)
object KlangScriptSuperSineExtensions {

    /** Oscillator frequency. Omit to track the playing note's pitch (the default). */
    @KlangScript.Method
    fun freq(self: IgnitorDsl.SuperSine, freq: IgnitorDslLike): IgnitorDsl.SuperSine =
        self.copy(freq = freq.toIgnitorDsl())

    /** Number of detuned voices in the stack (default 8). */
    @KlangScript.Method
    fun voices(self: IgnitorDsl.SuperSine, voices: IgnitorDslLike): IgnitorDsl.SuperSine =
        self.copy(voices = voices.toIgnitorDsl())

    /** Unison frequency spread between the voices (default 0.2). Same as the pattern-level `.spread()`. */
    @KlangScript.Method
    fun spread(self: IgnitorDsl.SuperSine, spread: IgnitorDslLike): IgnitorDsl.SuperSine =
        self.copy(spread = spread.toIgnitorDsl())

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.SuperSine, analog: IgnitorDslLike): IgnitorDsl.SuperSine =
        self.copy(analog = analog.toIgnitorDsl())

    /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward (default 1.2). */
    @KlangScript.Method
    fun spreadPower(self: IgnitorDsl.SuperSine, spreadPower: Double): IgnitorDsl.SuperSine =
        self.copy(spreadPower = spreadPower)

    /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice (default 0.1). */
    @KlangScript.Method
    fun sideAtten(self: IgnitorDsl.SuperSine, sideAtten: Double): IgnitorDsl.SuperSine =
        self.copy(sideAtten = sideAtten)

    /** Per-voice random amplitude offset (±fraction); 0 = off (default 0.15). */
    @KlangScript.Method
    fun gainJitter(self: IgnitorDsl.SuperSine, gainJitter: Double): IgnitorDsl.SuperSine =
        self.copy(gainJitter = gainJitter)

    /** Fraction of [gainJitter] the on-pitch center voice gets: 0 = stable center, 1 = jittered like sides (default 0.4). */
    @KlangScript.Method
    fun centerJitter(self: IgnitorDsl.SuperSine, centerJitter: Double): IgnitorDsl.SuperSine =
        self.copy(centerJitterScale = centerJitter)

    /** Banded start-phase selection (phase pool): 0 = off — bit-identical legacy random — 1 = on (default 0). */
    @KlangScript.Method
    fun phasePool(self: IgnitorDsl.SuperSine, phasePool: Double): IgnitorDsl.SuperSine =
        self.copy(phasePool = phasePool)

    /** Candidate phase sets scored per note when the phase pool is on (default 40 — deep search for the
     *  supersine's rare high band; engine caps at 64). */
    @KlangScript.Method
    fun drawTries(self: IgnitorDsl.SuperSine, drawTries: Double): IgnitorDsl.SuperSine =
        self.copy(drawTries = drawTries)

    /** Accepted fundamental-coherence band, lower edge: 0 = cancelled, 1 = phase-aligned (default 0.5 — K IS the note). */
    @KlangScript.Method
    fun kMin(self: IgnitorDsl.SuperSine, kMin: Double): IgnitorDsl.SuperSine =
        self.copy(kMin = kMin)

    /** Accepted fundamental-coherence band, upper edge (default 0.8). */
    @KlangScript.Method
    fun kMax(self: IgnitorDsl.SuperSine, kMax: Double): IgnitorDsl.SuperSine =
        self.copy(kMax = kMax)
    /** Pool vocabulary size per (orbit, unison, profile, band) key; engine caps at 4096 (default 1000). */
    @KlangScript.Method
    fun poolSize(self: IgnitorDsl.SuperSine, poolSize: Double): IgnitorDsl.SuperSine =
        self.copy(poolSize = poolSize)

    /** Notes between fresh pool draws (random eviction); 0 = frozen pool (default 10). */
    @KlangScript.Method
    fun refreshEvery(self: IgnitorDsl.SuperSine, refreshEvery: Double): IgnitorDsl.SuperSine =
        self.copy(refreshEvery = refreshEvery)

    /** Pool entry selection: 0 = roundRobin (default), 1 = random. */
    @KlangScript.Method
    fun selection(self: IgnitorDsl.SuperSine, selection: Double): IgnitorDsl.SuperSine =
        self.copy(selection = selection)
}
