/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Door parity for the effect wrappers with knobs: `.eq(configure)`, `.phaser(..., configure)`,
 * `.shimmer(..., configure)`. The script form with a configure lambda and the Kotlin form (the
 * audio_bridge `eq()/band()/tap()` extensions, or the node data class) must build equal nodes.
 */
class KlangScriptEffectBuilderSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    val saw = IgnitorDsl.Sawtooth()

    "eq: script lambda == Kotlin eq().band().tap()" {
        ks("Osc.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7))") shouldBe
                saw.eq().band(300.0, 1.0, -4.0).tap(850.0, 0.707, 1.7)
    }

    "eq: the Kotlin door of the stdlib takes the same lambda" {
        ks("Osc.saw().eq(e => e.band(300, 1.0, -4))") shouldBe
                KlangScriptOscExtensions.eq(saw, configure = { it.band(300.0, 1.0, -4.0) })
    }

    "eq: a lambda that returns nothing is a script-level type error" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.saw().eq(e => { e.band(300) })") }
    }

    "phaser: wet and dryFloor via the lambda == the node fields" {
        val dsl = ks("Osc.saw().phaser(0.3, x => x.wet(0.25).dryFloor(0.1))") as IgnitorDsl.Phaser
        dsl shouldBe IgnitorDsl.Phaser(
            inner = saw, rate = IgnitorDsl.Constant(0.3),
            wet = IgnitorDsl.Constant(0.25), dryFloor = IgnitorDsl.Constant(0.1),
        )
    }

    "phaser: center and sweep stay door parameters, the lambda floats past them" {
        val dsl = ks("Osc.saw().phaser(0.3, 800, 600, x => x.wet(0.4))") as IgnitorDsl.Phaser
        dsl.center shouldBe IgnitorDsl.Constant(800.0)
        dsl.sweep shouldBe IgnitorDsl.Constant(600.0)
        dsl.wet shouldBe IgnitorDsl.Constant(0.4)
    }

    "shimmer: defaults, pitches default when omitted, wet via the lambda" {
        val dsl = ks("Osc.saw().shimmer(x => x.wet(0.3))") as IgnitorDsl.Shimmer
        dsl shouldBe IgnitorDsl.Shimmer(inner = saw, wet = IgnitorDsl.Constant(0.3))
        dsl.pitches shouldBe listOf(0.0, 7.0, 12.0)
    }

    "shimmer: explicit pitches array still binds positionally before the lambda" {
        val dsl = ks("Osc.saw().shimmer(0.4, 3000, [0, 4, 7, 11], x => x.dryFloor(0.2))") as IgnitorDsl.Shimmer
        dsl.pitches shouldBe listOf(0.0, 4.0, 7.0, 11.0)
        dsl.feedback shouldBe IgnitorDsl.Constant(0.4)
        dsl.tone shouldBe IgnitorDsl.Constant(3000.0)
        dsl.dryFloor shouldBe IgnitorDsl.Constant(0.2)
    }

    "the old chain knobs are gone from the nodes" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.saw().phaser(0.3).wet(0.25)") }
        shouldThrow<KlangScriptTypeError> { ks("Osc.saw().shimmer().wet(0.3)") }
        shouldThrow<KlangScriptTypeError> { ks("Osc.saw().eq().band(1200)") }
    }
})
