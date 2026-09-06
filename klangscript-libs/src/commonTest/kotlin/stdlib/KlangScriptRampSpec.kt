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
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Dual-language equivalence for the `Osc.ramp(freq, configure)` door and its [OscRampBuilder]: script
 * lambda vs Kotlin door / data class `.copy()`, structurally equal [IgnitorDsl.Ramp] nodes.
 */
class KlangScriptRampSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptOsc.ramp()

    "Osc.ramp(): script == Kotlin door, all defaults" {
        ks("Osc.ramp()") shouldBe node()
    }

    "freq is the door's first parameter: Osc.ramp(220)" {
        ks("Osc.ramp(220)") shouldBe (node() as IgnitorDsl.Ramp).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "analog(5.0)" {
        ks("Osc.ramp(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.Ramp).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "resetSamples(4.0)" {
        ks("Osc.ramp(x => x.resetSamples(4.0))") shouldBe (node() as IgnitorDsl.Ramp).copy(resetSamples = 4.0)
    }

    "shapeMax(0.3)" {
        ks("Osc.ramp(x => x.shapeMax(0.3))") shouldBe (node() as IgnitorDsl.Ramp).copy(shapeMax = 0.3)
    }

    "every knob in one lambda" {
        ks("Osc.ramp(110, x => x.analog(5.0).resetSamples(4.0).shapeMax(0.3))") shouldBe (node() as IgnitorDsl.Ramp).copy(freq = IgnitorDsl.Constant(110.0), analog = IgnitorDsl.Constant(5.0), resetSamples = 4.0, shapeMax = 0.3)
    }

    "the Kotlin door takes the same lambda" {
        ks("Osc.ramp(x => x.shapeMax(0.3))") shouldBe KlangScriptOsc.ramp(configure = { it.shapeMax(0.3) })
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Osc.ramp(x => x.shapeMax(0.3)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.Ramp>()
        inner.shapeMax shouldBe 0.3
    }

    "a lambda that returns nothing is a script-level type error" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.ramp(x => { x.shapeMax(0.3) })") }
    }
})
