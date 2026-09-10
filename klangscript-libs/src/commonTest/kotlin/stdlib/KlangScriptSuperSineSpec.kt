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
 * Dual-language equivalence for the `Osc.supersine(freq, configure)` door and its [OscSuperSineBuilder].
 *
 * Each case expresses the SAME thing two ways, as KlangScript source run through the full engine
 * (parse, interpret, native interop, the configure lambda floating into its slot) and as the
 * Kotlin door with a Kotlin lambda or a data class `.copy()`, then asserts the resulting
 * [IgnitorDsl.SuperSine] nodes are structurally equal. Comparing against `.copy()` keeps the check
 * independent of the builder: a knob writing the wrong field is caught.
 */
class KlangScriptSuperSineSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptOsc.supersine()

    "Osc.supersine(): script == Kotlin door, all defaults" {
        ks("Osc.supersine()") shouldBe node()
    }

    "freq is the door's first parameter, not a knob: Osc.supersine(220)" {
        ks("Osc.supersine(220)") shouldBe (node() as IgnitorDsl.SuperSine).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(9) via the configure lambda" {
        ks("Osc.supersine(x => x.voices(9))") shouldBe (node() as IgnitorDsl.SuperSine).copy(voices = IgnitorDsl.Constant(9.0))
    }

    "voices accepts an Osc graph (control-rate)" {
        ks("Osc.supersine(x => x.voices(Osc.sine(0.5)))") shouldBe
                (node() as IgnitorDsl.SuperSine).copy(voices = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.5)))
    }

    "spread(0.3)" {
        ks("Osc.supersine(x => x.spread(0.3))") shouldBe (node() as IgnitorDsl.SuperSine).copy(spread = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.supersine(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.SuperSine).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "analogSpread(0): the voices drift on ONE shared lane instead of their own" {
        ks("Osc.supersine(x => x.analogSpread(0))") shouldBe
                (node() as IgnitorDsl.SuperSine).copy(analogSpread = IgnitorDsl.Constant(0.0))
    }

    "spreadPower(1.5)" {
        ks("Osc.supersine(x => x.spreadPower(1.5))") shouldBe (node() as IgnitorDsl.SuperSine).copy(spreadPower = 1.5)
    }

    "sideAtten(0.25)" {
        ks("Osc.supersine(x => x.sideAtten(0.25))") shouldBe (node() as IgnitorDsl.SuperSine).copy(sideAtten = 0.25)
    }

    "gainJitter(0.0)" {
        ks("Osc.supersine(x => x.gainJitter(0.0))") shouldBe (node() as IgnitorDsl.SuperSine).copy(gainJitter = 0.0)
    }

    "centerJitter(1.0) maps to centerJitterScale" {
        ks("Osc.supersine(x => x.centerJitter(1.0))") shouldBe (node() as IgnitorDsl.SuperSine).copy(centerJitterScale = 1.0)
    }

    "phasePool(): on with family defaults (sync guard: knob defaults == node defaults)" {
        ks("Osc.supersine(x => x.phasePool())") shouldBe (node() as IgnitorDsl.SuperSine).copy(phasePool = 1.0)
    }

    "phasePool(on = 0, kMin = 0.2): all-named subset" {
        ks("Osc.supersine(x => x.phasePool(on = 0, kMin = 0.2))") shouldBe
                (node() as IgnitorDsl.SuperSine).copy(phasePool = 0.0, kMin = 0.2)
    }

    "phasePool(refreshEvery = 0): a named arg skips the leading literal defaults" {
        ks("Osc.supersine(x => x.phasePool(refreshEvery = 0))") shouldBe
                (node() as IgnitorDsl.SuperSine).copy(phasePool = 1.0, refreshEvery = 0.0)
    }

    "every knob in one lambda, freq on the door" {
        val code = "Osc.supersine(110, x => x.voices(11).spread(0.12).analog(4.0)" +
                ".analogSpread(0.25)" +
                ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)" +
                ".phasePool(1, 0.2, 0.7, 8, 64, 5, \"random\", 8))"
        ks(code) shouldBe (node() as IgnitorDsl.SuperSine).copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(11.0),
            spread = IgnitorDsl.Constant(0.12),
            analog = IgnitorDsl.Constant(4.0),
            analogSpread = IgnitorDsl.Constant(0.25),
            spreadPower = 1.4,
            sideAtten = 0.2,
            gainJitter = 0.1,
            centerJitterScale = 0.6,
            phasePool = 1.0,
            drawTries = 8.0,
            kMin = 0.2,
            kMax = 0.7,
            poolSize = 64.0,
            refreshEvery = 5.0,
            selection = "random",
            warmup = 8.0,
        )
    }

    "the Kotlin door takes the same lambda" {
        ks("Osc.supersine(x => x.voices(11).spread(0.12))") shouldBe
                KlangScriptOsc.supersine(configure = { it.voices(11).spread(0.12) })
    }

    "named configure binds too" {
        ks("Osc.supersine(configure = x => x.voices(3))") shouldBe (node() as IgnitorDsl.SuperSine).copy(voices = IgnitorDsl.Constant(3.0))
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Osc.supersine(x => x.spreadPower(1.5)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperSine>()
        inner.spreadPower shouldBe 1.5
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Osc.supersine(x => { x.voices(3) })") }
        err.message shouldBe "the configure lambda of Osc.supersine returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a lambda that returns something else is a script-level type error, not a cast failure" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.supersine(x => 5)") }
    }
})
