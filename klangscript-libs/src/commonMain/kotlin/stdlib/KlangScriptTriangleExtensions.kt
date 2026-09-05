/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Fluent config methods for the triangle oscillator (`Osc.tri()`) ([IgnitorDsl.Triangle]). Each returns a new
 * [IgnitorDsl.Triangle], so they chain — put these *before* the base wrappers
 * (`.lowpass()`/`.adsr()`), which return the base [IgnitorDsl] and so come last
 * (config-first ordering).
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Triangle::class)
object KlangScriptTriangleExtensions {

    /** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. */
    @KlangScript.Method
    fun analog(self: IgnitorDsl.Triangle, analog: IgnitorDslLike): IgnitorDsl.Triangle =
        self.copy(analog = analog.toIgnitorDsl())
}
