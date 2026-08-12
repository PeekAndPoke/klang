/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.stdlib.KlangScriptSuperSquareExtensions.gainJitter

/**
 * Fluent config methods for the supersquare oscillator (`Osc.supersquare()`). Each returns a new
 * [IgnitorDsl.SuperSquare], so they chain — put these *before* the base wrappers (`.lowpass()`/`.adsr()`),
 * which return the base [IgnitorDsl] and so come last (config-first ordering).
 *
 * `voices`/`spread`/`analog`/`freq` accept an [IgnitorDslLike] (a number → [IgnitorDsl.Constant], or an
 * `Osc.*` graph for audio-rate modulation). The character knobs (`spreadPower`/`sideAtten`/`gainJitter`/
 * `centerJitter`) are plain scalars read once per voice — they mirror the `SUPERSQUARE_*` engine constants.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.SuperSquare::class)
object KlangScriptSuperSquareExtensions {

    /** Oscillator frequency. Omit to track the playing note's pitch (the default). */
    @KlangScript.Method
    fun freq(self: IgnitorDsl.SuperSquare, freq: IgnitorDslLike): IgnitorDsl.SuperSquare =
        self.copy(freq = freq.toIgnitorDsl())

    /** Number of detuned voices in the stack (default 8). */
    @KlangScript.Method
    fun voices(self: IgnitorDsl.SuperSquare, voices: IgnitorDslLike): IgnitorDsl.SuperSquare =
        self.copy(voices = voices.toIgnitorDsl())

    /** Unison frequency spread between the voices (default 0.2). Same as the pattern-level `.spread()`. */
    @KlangScript.Method
    fun spread(self: IgnitorDsl.SuperSquare, spread: IgnitorDslLike): IgnitorDsl.SuperSquare =
        self.copy(spread = spread.toIgnitorDsl())

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.SuperSquare, analog: IgnitorDslLike): IgnitorDsl.SuperSquare =
        self.copy(analog = analog.toIgnitorDsl())

    /** Detune spacing shape: 1 = even, >1 concentrates toward center, <1 spreads outward (default 1.2). */
    @KlangScript.Method
    fun spreadPower(self: IgnitorDsl.SuperSquare, spreadPower: Double): IgnitorDsl.SuperSquare =
        self.copy(spreadPower = spreadPower)

    /** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice (default 0.1). */
    @KlangScript.Method
    fun sideAtten(self: IgnitorDsl.SuperSquare, sideAtten: Double): IgnitorDsl.SuperSquare =
        self.copy(sideAtten = sideAtten)

    /** Per-voice random amplitude offset (±fraction); 0 = off (default 0.15). */
    @KlangScript.Method
    fun gainJitter(self: IgnitorDsl.SuperSquare, gainJitter: Double): IgnitorDsl.SuperSquare =
        self.copy(gainJitter = gainJitter)

    /** Fraction of [gainJitter] the on-pitch center voice gets: 0 = stable center, 1 = jittered like sides (default 0.4). */
    @KlangScript.Method
    fun centerJitter(self: IgnitorDsl.SuperSquare, centerJitter: Double): IgnitorDsl.SuperSquare =
        self.copy(centerJitterScale = centerJitter)
    /**
     * Configure the banded start-phase pool in ONE call — every param is an optional plain
     * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
     * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named
     * or all-positional — KlangScript forbids mixing). Defaults mirror the
     * `SUPERSQUARE`-family engine constants (guarded by the dual-language spec).
     *
     * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
     * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
     * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
     * @param drawTries candidate phase sets scored per draw (engine caps at 64).
     * @param poolSize vocabulary size per pool key (engine caps at 1024).
     * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
     * @param selection 0 = roundRobin (default), 1 = random.
     */
    @KlangScript.Method
    fun phasePool(
        self: IgnitorDsl.SuperSquare,
        on: Double = 1.0,
        kMin: Double = 0.30,
        kMax: Double = 0.55,
        drawTries: Double = 5.0,
        poolSize: Double = 256.0,
        refreshEvery: Double = 10.0,
        selection: Double = 0.0,
    ): IgnitorDsl.SuperSquare = self.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection,
    )
}
