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
 * Dual-language equivalence for the `Osc.supertri(freq, configure)` door and its [OscSuperTriBuilder].
 *
 * Each case expresses the SAME thing two ways, as KlangScript source run through the full engine
 * (parse, interpret, native interop, the configure lambda floating into its slot) and as the
 * Kotlin door with a Kotlin lambda or a data class `.copy()`, then asserts the resulting
 * [IgnitorDsl.SuperTri] nodes are structurally equal. Comparing against `.copy()` keeps the check
 * independent of the builder: a knob writing the wrong field is caught.
 */
class KlangScriptSuperTriSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun node() = KlangScriptOsc.supertri()

    "Osc.supertri(): script == Kotlin door, all defaults" {
        ks("Osc.supertri()") shouldBe node()
    }

    "freq is the door's first parameter, not a knob: Osc.supertri(220)" {
        ks("Osc.supertri(220)") shouldBe (node() as IgnitorDsl.SuperTri).copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(9) via the configure lambda" {
        ks("Osc.supertri(x => x.voices(9))") shouldBe (node() as IgnitorDsl.SuperTri).copy(voices = IgnitorDsl.Constant(9.0))
    }

    "voices accepts an Osc graph (control-rate)" {
        ks("Osc.supertri(x => x.voices(Osc.sine(0.5)))") shouldBe
                (node() as IgnitorDsl.SuperTri).copy(voices = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.5)))
    }

    "spread(0.3)" {
        ks("Osc.supertri(x => x.spread(0.3))") shouldBe (node() as IgnitorDsl.SuperTri).copy(spread = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.supertri(x => x.analog(5.0))") shouldBe (node() as IgnitorDsl.SuperTri).copy(analog = IgnitorDsl.Constant(5.0))
    }

    "spreadPower(1.5)" {
        ks("Osc.supertri(x => x.spreadPower(1.5))") shouldBe (node() as IgnitorDsl.SuperTri).copy(spreadPower = 1.5)
    }

    "sideAtten(0.25)" {
        ks("Osc.supertri(x => x.sideAtten(0.25))") shouldBe (node() as IgnitorDsl.SuperTri).copy(sideAtten = 0.25)
    }

    "gainJitter(0.0)" {
        ks("Osc.supertri(x => x.gainJitter(0.0))") shouldBe (node() as IgnitorDsl.SuperTri).copy(gainJitter = 0.0)
    }

    "centerJitter(1.0) maps to centerJitterScale" {
        ks("Osc.supertri(x => x.centerJitter(1.0))") shouldBe (node() as IgnitorDsl.SuperTri).copy(centerJitterScale = 1.0)
    }

    "phasePool(): on with family defaults (sync guard: knob defaults == node defaults)" {
        ks("Osc.supertri(x => x.phasePool())") shouldBe (node() as IgnitorDsl.SuperTri).copy(phasePool = 1.0)
    }

    "phasePool(on = 0, kMin = 0.2): all-named subset" {
        ks("Osc.supertri(x => x.phasePool(on = 0, kMin = 0.2))") shouldBe
                (node() as IgnitorDsl.SuperTri).copy(phasePool = 0.0, kMin = 0.2)
    }

    "phasePool(refreshEvery = 0): a named arg skips the leading literal defaults" {
        ks("Osc.supertri(x => x.phasePool(refreshEvery = 0))") shouldBe
                (node() as IgnitorDsl.SuperTri).copy(phasePool = 1.0, refreshEvery = 0.0)
    }

    "every knob in one lambda, freq on the door" {
        val code = "Osc.supertri(110, x => x.voices(11).spread(0.12).analog(4.0)" +
                ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)" +
                ".phasePool(1, 0.2, 0.7, 8, 64, 5, \"random\", 8))"
        ks(code) shouldBe (node() as IgnitorDsl.SuperTri).copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(11.0),
            spread = IgnitorDsl.Constant(0.12),
            analog = IgnitorDsl.Constant(4.0),
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
        ks("Osc.supertri(x => x.voices(11).spread(0.12))") shouldBe
                KlangScriptOsc.supertri(configure = { it.voices(11).spread(0.12) })
    }

    "named configure binds too" {
        ks("Osc.supertri(configure = x => x.voices(3))") shouldBe (node() as IgnitorDsl.SuperTri).copy(voices = IgnitorDsl.Constant(3.0))
    }

    "processing goes OUTSIDE the lambda: the wrapper sees the configured node" {
        val dsl = ks("Osc.supertri(x => x.spreadPower(1.5)).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperTri>()
        inner.spreadPower shouldBe 1.5
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Osc.supertri(x => { x.voices(3) })") }
        err.message shouldBe "the configure lambda of Osc.supertri returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a lambda that returns something else is a script-level type error, not a cast failure" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.supertri(x => 5)") }
    }
})
