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
 * Dual-language equivalence for the `Osc.superpluck(freq, configure)` door and its
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

    fun node() = KlangScriptOsc.superpluck()

    "Osc.superpluck(): script == Kotlin door, all defaults" {
        ks("Osc.superpluck()") shouldBe node()
    }

    "freq is the door's first parameter, not a knob: Osc.superpluck(220)" {
        ks("Osc.superpluck(220)") shouldBe (node() as IgnitorDsl.SuperPluck).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(6) via the configure lambda" {
        ks("Osc.superpluck(x => x.voices(6))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(voices = IgnitorDsl.Constant(6.0))
    }

    "spread(0.15)" {
        ks("Osc.superpluck(x => x.spread(0.15))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(spread = IgnitorDsl.Constant(0.15))
    }

    "decay(0.99)" {
        ks("Osc.superpluck(x => x.decay(0.99))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(decay = IgnitorDsl.Constant(0.99))
    }

    "brightness(0.45)" {
        ks("Osc.superpluck(x => x.brightness(0.45))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(brightness = IgnitorDsl.Constant(0.45))
    }

    "pickPosition(0.3)" {
        ks("Osc.superpluck(x => x.pickPosition(0.3))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(pickPosition = IgnitorDsl.Constant(0.3))
    }

    "stiffness(0.2)" {
        ks("Osc.superpluck(x => x.stiffness(0.2))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(stiffness = IgnitorDsl.Constant(0.2))
    }

    "analog(5.0)" {
        ks("Osc.superpluck(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.SuperPluck).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "analogSpread(0): the strings drift on ONE shared lane instead of their own" {
        ks("Osc.superpluck(x => x.analogSpread(0))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(analogSpread = IgnitorDsl.Constant(0.0))
    }

    "every knob in one lambda, freq on the door" {
        val code = "Osc.superpluck(110, x => x.voices(6).spread(0.15).decay(0.99)" +
                ".brightness(0.45).pickPosition(0.3).stiffness(0.2).analog(4.0).analogSpread(0.25))"
        ks(code) shouldBe (node() as IgnitorDsl.SuperPluck).copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(6.0),
            spread = IgnitorDsl.Constant(0.15),
            decay = IgnitorDsl.Constant(0.99),
            brightness = IgnitorDsl.Constant(0.45),
            pickPosition = IgnitorDsl.Constant(0.3),
            stiffness = IgnitorDsl.Constant(0.2),
            analog = IgnitorDsl.Constant(4.0),
            analogSpread = IgnitorDsl.Constant(0.25),
        )
    }

    "the Kotlin door takes the same lambda" {
        ks("Osc.superpluck(x => x.voices(6).spread(0.15))") shouldBe
                KlangScriptOsc.superpluck(configure = { it.voices(6).spread(0.15) })
    }

    "named configure binds too" {
        ks("Osc.superpluck(configure = x => x.voices(3))") shouldBe
                (node() as IgnitorDsl.SuperPluck).copy(voices = IgnitorDsl.Constant(3.0))
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Osc.superpluck(x => x.stiffness(0.2)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperPluck>()
        inner.stiffness shouldBe IgnitorDsl.Constant(0.2)
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Osc.superpluck(x => { x.voices(3) })") }
        err.message shouldBe "the configure lambda of Osc.superpluck returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a lambda that returns something else is a script-level type error, not a cast failure" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.superpluck(x => 5)") }
    }
})
