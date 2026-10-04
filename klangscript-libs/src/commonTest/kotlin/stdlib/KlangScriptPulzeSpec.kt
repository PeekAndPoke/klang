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
 * Dual-language equivalence for the `Ignitor.square(freq, configure)` door and its [OscSquareBuilder]: script
 * lambda vs Kotlin door / data class `.copy()`, structurally equal [IgnitorDsl.Pulze] nodes.
 */
class KlangScriptPulzeSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptIgnitor.square()

    "Ignitor.square(): script == Kotlin door, all defaults" {
        ks("Ignitor.square()") shouldBe node()
    }

    "freq is the door's first parameter: Ignitor.square(220)" {
        ks("Ignitor.square(220)") shouldBe (node() as IgnitorDsl.Pulze).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "duty(0.3)" {
        ks("Ignitor.square(x => x.duty(0.3))") shouldBe (node() as IgnitorDsl.Pulze).copy(duty = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Ignitor.square(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.Pulze).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "flankSamples(4.0)" {
        ks("Ignitor.square(x => x.flankSamples(4.0))") shouldBe (node() as IgnitorDsl.Pulze).copy(flankSamples = 4.0)
    }

    "riseFlank(0.5)" {
        ks("Ignitor.square(x => x.riseFlank(0.5))") shouldBe (node() as IgnitorDsl.Pulze).copy(riseFlank = 0.5)
    }

    "fallFlank(0.5)" {
        ks("Ignitor.square(x => x.fallFlank(0.5))") shouldBe (node() as IgnitorDsl.Pulze).copy(fallFlank = 0.5)
    }

    "every knob in one lambda" {
        ks("Ignitor.square(110, x => x.duty(0.3).analog(5.0).flankSamples(4.0).riseFlank(0.5).fallFlank(0.5))") shouldBe (node() as IgnitorDsl.Pulze).copy(freq = IgnitorDsl.Constant(110.0), duty = IgnitorDsl.Constant(0.3), analog = IgnitorDsl.Constant(5.0), flankSamples = 4.0, riseFlank = 0.5, fallFlank = 0.5)
    }

    "the Kotlin door takes the same lambda" {
        ks("Ignitor.square(x => x.fallFlank(0.5))") shouldBe KlangScriptIgnitor.square(configure = { it.fallFlank(0.5) })
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Ignitor.square(x => x.fallFlank(0.5)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.Pulze>()
        inner.fallFlank shouldBe 0.5
    }

    "a lambda that returns nothing is a script-level type error" {
        shouldThrow<KlangScriptTypeError> { ks("Ignitor.square(x => { x.fallFlank(0.5) })") }
    }
})
