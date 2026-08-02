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

    /** How much of the bus is sent into the reverb (default 0.25; 0.0 = off). */
    @KlangScript.Method
    fun wet(self: MasterStageDsl.Reverb, wet: Double): MasterStageDsl.Reverb = self.copy(wet = wet)

    /** Freeverb room size (default 0.5). */
    @KlangScript.Method
    fun roomSize(self: MasterStageDsl.Reverb, size: Double): MasterStageDsl.Reverb =
        self.copy(roomSize = size)

    /** Freeverb high-frequency damping (default 0.5). */
    @KlangScript.Method
    fun damp(self: MasterStageDsl.Reverb, damp: Double): MasterStageDsl.Reverb = self.copy(damp = damp)
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

    /** Feedback amount (default 0.3); ≥ 1.0 is unstable but bounded by the DSP's soft cap. */
    @KlangScript.Method
    fun feedback(self: MasterStageDsl.Delay, feedback: Double): MasterStageDsl.Delay =
        self.copy(feedback = feedback)
}
