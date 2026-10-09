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
 * Dual-language equivalence for the `Ignitor.superpluck(freq, configure)` door and its
 * [OscSuperPluckBuilder].
 *
 * Each case expresses the SAME thing two ways, as KlangScript source run through the full engine
 * (parse, interpret, native interop, the configure lambda floating into its slot) and as the
 * Kotlin door with a Kotlin lambda or a data class `.copy()`, then asserts the resulting
 * [IgnitorDsl.SuperPluck] nodes are structurally equal. Comparing against `.copy()` keeps the check
 * independent of the builder: a knob writing the wrong field is caught.
 */
class KlangScriptSuperPluckSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptIgnitor.superpluck()

    "Ignitor.superpluck(): script == Kotlin door, all defaults" {
        ks("Ignitor.superpluck()") shouldBe node()
    }

    "freq is the door's first parameter, not a knob: Ignitor.superpluck(220)" {
        ks("Ignitor.superpluck(220)") shouldBe (node() as IgnitorDsl.SuperPluck).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(6) via the configure lambda" {
        ks("Ignitor.superpluck(x => x.voices(6))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(voices = IgnitorDsl.Constant(6.0))
    }

    "spread(0.15)" {
        ks("Ignitor.superpluck(x => x.spread(0.15))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(spread = IgnitorDsl.Constant(0.15))
    }

    "feedback(0.99)" {
        ks("Ignitor.superpluck(x => x.feedback(0.99))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(feedback = IgnitorDsl.Constant(0.99))
    }

    "brightness(0.45)" {
        ks("Ignitor.superpluck(x => x.brightness(0.45))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(brightness = IgnitorDsl.Constant(0.45))
    }

    "pickPosition(0.3)" {
        ks("Ignitor.superpluck(x => x.pickPosition(0.3))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(pickPosition = IgnitorDsl.Constant(0.3))
    }

    "stiffness(0.2)" {
        ks("Ignitor.superpluck(x => x.stiffness(0.2))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(stiffness = IgnitorDsl.Constant(0.2))
    }

    "analog(5.0)" {
        ks("Ignitor.superpluck(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "analogSpread(0): the strings drift on ONE shared lane instead of their own" {
        ks("Ignitor.superpluck(x => x.analogSpread(0))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(analogSpread = IgnitorDsl.Constant(0.0))
    }

    "every knob in one lambda, freq on the door" {
        val code = "Ignitor.superpluck(110, x => x.voices(6).spread(0.15).feedback(0.99)" +
                ".brightness(0.45).pickPosition(0.3).stiffness(0.2).analog(4.0).analogSpread(0.25))"
        ks(code) shouldBe (node() as IgnitorDsl.SuperPluck).copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(6.0),
            spread = IgnitorDsl.Constant(0.15),
            feedback = IgnitorDsl.Constant(0.99),
            brightness = IgnitorDsl.Constant(0.45),
            pickPosition = IgnitorDsl.Constant(0.3),
            stiffness = IgnitorDsl.Constant(0.2),
            analog = IgnitorDsl.Constant(4.0),
            analogSpread = IgnitorDsl.Constant(0.25),
        )
    }

    "the Kotlin door takes the same lambda" {
        ks("Ignitor.superpluck(x => x.voices(6).spread(0.15))") shouldBe
                KlangScriptIgnitor.superpluck(configure = { it.voices(6).spread(0.15) })
    }

    "named configure binds too" {
        ks("Ignitor.superpluck(configure = x => x.voices(3))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(voices = IgnitorDsl.Constant(3.0))
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Ignitor.superpluck(x => x.stiffness(0.2)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperPluck>()
        inner.stiffness shouldBe IgnitorDsl.Constant(0.2)
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Ignitor.superpluck(x => { x.voices(3) })") }
        err.message shouldBe "the configure lambda of Ignitor.superpluck returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a lambda that returns something else is a script-level type error, not a cast failure" {
        shouldThrow<KlangScriptTypeError> { ks("Ignitor.superpluck(x => 5)") }
    }
})
