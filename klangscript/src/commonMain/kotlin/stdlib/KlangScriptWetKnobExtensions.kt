/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * The shared wet knob (C4, filter unification), typed onto each additive effect node — the
 * same shape as the Eq band methods: the knob exists ONLY on the effect it belongs to, so
 * `.wet(0.3)` on a plain oscillator is an error and the effect call is the entry point.
 *
 * `wet` is the wet/dry balance in [0, 1] under the one shared law: `0` is a bit-exact
 * bypass, `1` is effect-only, `0.5` an equal mix. `dryFloor` is the minimum dry
 * coefficient (default 0): raising it keeps at least that much dry in at every `wet`;
 * at `1.0` the effect becomes purely additive, like the orbit-side phaser. (It is called
 * `dryFloor`, not `floor` — `floor()` is already the arithmetic round-down.)
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Phaser::class)
object KlangScriptPhaserKnobExtensions {

    /** Sets the wet/dry balance on this phaser: `.phaser(rate).wet(0.3)`. Default 0.5. */
    @KlangScript.Method
    fun wet(self: IgnitorDsl.Phaser, value: IgnitorDslLike): IgnitorDsl.Phaser =
        self.copy(wet = value.toIgnitorDsl())

    /** Sets the minimum dry coefficient on this phaser: `.phaser(rate).dryFloor(0.2)`. Default 0. */
    @KlangScript.Method
    fun dryFloor(self: IgnitorDsl.Phaser, value: IgnitorDslLike): IgnitorDsl.Phaser =
        self.copy(dryFloor = value.toIgnitorDsl())
}

/** See [KlangScriptPhaserKnobExtensions] — the same shared wet knob, on the shimmer node. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl.Shimmer::class)
object KlangScriptShimmerKnobExtensions {

    /** Sets the wet/dry balance on this shimmer: `.shimmer().wet(0.4)`. Default 0.5. */
    @KlangScript.Method
    fun wet(self: IgnitorDsl.Shimmer, value: IgnitorDslLike): IgnitorDsl.Shimmer =
        self.copy(wet = value.toIgnitorDsl())

    /** Sets the minimum dry coefficient on this shimmer: `.shimmer().dryFloor(0.2)`. Default 0. */
    @KlangScript.Method
    fun dryFloor(self: IgnitorDsl.Shimmer, value: IgnitorDslLike): IgnitorDsl.Shimmer =
        self.copy(dryFloor = value.toIgnitorDsl())
}
