/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Config methods on the master gain stage (`MasterFx.gain()`). Mirror of the `Stage.*` extensions
 * for the voice pipeline: each returns a new stage, so chain before adding the next one.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(MasterStageDsl.Gain::class)
object KlangScriptMasterGainExtensions {

    /** Linear gain factor (default 1.0 = unity; 2.0 ≈ +6 dB). */
    @KlangScript.Method
    fun gain(self: MasterStageDsl.Gain, gain: Double): MasterStageDsl.Gain = self.copy(gain = gain)
}

/** Config methods on the master limiter stage (`MasterFx.limiter()`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(MasterStageDsl.Limiter::class)
object KlangScriptMasterLimiterExtensions {

    /** Ceiling in dBFS (default -1.0). */
    @KlangScript.Method
    fun thresholdDb(self: MasterStageDsl.Limiter, db: Double): MasterStageDsl.Limiter =
        self.copy(thresholdDb = db)

    /** Compression ratio (default 20.0 ≈ brick wall). */
    @KlangScript.Method
    fun ratio(self: MasterStageDsl.Limiter, ratio: Double): MasterStageDsl.Limiter =
        self.copy(ratio = ratio)

    /** Soft-knee width in dB (default 2.0). A hard corner injects harmonics on every crossing. */
    @KlangScript.Method
    fun kneeDb(self: MasterStageDsl.Limiter, db: Double): MasterStageDsl.Limiter =
        self.copy(kneeDb = db)

    /** Envelope attack in seconds (default 0.001). Short keeps transient punch. */
    @KlangScript.Method
    fun attack(self: MasterStageDsl.Limiter, seconds: Double): MasterStageDsl.Limiter =
        self.copy(attackSeconds = seconds)

    /** Envelope release in seconds (default 0.1). */
    @KlangScript.Method
    fun release(self: MasterStageDsl.Limiter, seconds: Double): MasterStageDsl.Limiter =
        self.copy(releaseSeconds = seconds)
}

/** Config methods on the master reverb stage (`MasterFx.reverb()`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(MasterStageDsl.Reverb::class)
object KlangScriptMasterReverbExtensions {

    /** How much of the bus is sent into the reverb (default 0.25; 0.0 = off). Orbit twin: `room()`. */
    @KlangScript.Method
    fun wet(self: MasterStageDsl.Reverb, wet: Double): MasterStageDsl.Reverb = self.copy(wet = wet)

    /**
     * Tail length, on the **same scale as sprudel `roomsize()`** — typical 1..10, default 5.
     *
     * 3 ≈ a 1 s tail, 5 ≈ 1.4 s, 10 ≈ 12.5 s; the shortest reachable is ~0.7 s. Above 10 is bounded:
     * past unity the comb network has no steady state and runs away, so there is nothing there.
     */
    @KlangScript.Method
    fun roomSize(self: MasterStageDsl.Reverb, size: Double): MasterStageDsl.Reverb =
        self.copy(roomSize = size)

    /** High-frequency damping, 0 = bright .. 1 = dark (default 0.5). **Ignored when `roomLp` is set.** */
    @KlangScript.Method
    fun damp(self: MasterStageDsl.Reverb, damp: Double): MasterStageDsl.Reverb = self.copy(damp = damp)

    /**
     * **Overrides `roomSize`** for the tail — and is NOT on the same scale: this is the normalized
     * **0..1** value (0 ≈ 0.7 s, 1 ≈ 12.5 s), and despite the name it is not a time.
     * Orbit twin: `roomfade()` / `rfade()`.
     */
    @KlangScript.Method
    fun roomFade(self: MasterStageDsl.Reverb, amount: Double): MasterStageDsl.Reverb =
        self.copy(roomFade = amount)

    /**
     * High-frequency damping as an absolute cutoff **in Hz**; overrides `damp`.
     * Orbit twin: `roomlp()` / `rlp()`.
     */
    @KlangScript.Method
    fun roomLp(self: MasterStageDsl.Reverb, hz: Double): MasterStageDsl.Reverb = self.copy(roomLp = hz)

}

/** Config methods on the master delay stage (`MasterFx.delay()`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(MasterStageDsl.Delay::class)
object KlangScriptMasterDelayExtensions {

    /** How much of the bus is sent into the delay (default 0.25; 0.0 = off). */
    @KlangScript.Method
    fun wet(self: MasterStageDsl.Delay, wet: Double): MasterStageDsl.Delay = self.copy(wet = wet)

    /** Delay time in seconds (default 0.25). */
    @KlangScript.Method
    fun time(self: MasterStageDsl.Delay, seconds: Double): MasterStageDsl.Delay =
        self.copy(timeSeconds = seconds)

    /**
     * Feedback amount (default 0.3). At or above 1.0 the delay recirculates without loss and
     * self-oscillates — allowed, with `cap` deciding how loud. Orbit twin: `delayfeedback()`.
     */
    @KlangScript.Method
    fun feedback(self: MasterStageDsl.Delay, feedback: Double): MasterStageDsl.Delay =
        self.copy(feedback = feedback)

    /**
     * Ceiling the feedback saturates toward (default 1.0 = unchanged).
     * Orbit twin: `delaycap()` / `dcap()`.
     */
    @KlangScript.Method
    fun cap(self: MasterStageDsl.Delay, cap: Double): MasterStageDsl.Delay = self.copy(cap = cap)
}
