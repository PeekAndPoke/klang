/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Fluent config methods for the impulse oscillator (`Osc.impulse()`) ([IgnitorDsl.Impulse]). Each returns a new
 * [IgnitorDsl.Impulse], so they chain — put these *before* the base wrappers
 * (`.lowpass()`/`.adsr()`), which return the base [IgnitorDsl] and so come last
 * (config-first ordering).
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Impulse::class)
object KlangScriptImpulseExtensions {

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.Impulse, analog: IgnitorDslLike): IgnitorDsl.Impulse =
        self.copy(analog = analog.toIgnitorDsl())
}
