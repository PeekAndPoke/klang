/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Dual-language equivalence for the typed superramp DSL (Phase 2). Mirrors [KlangScriptSuperSawSpec].
 *
 * Each case expresses the SAME thing two ways — as KlangScript source run through the full engine, and as
 * the Kotlin builder + data class `.copy()` — then asserts the resulting [IgnitorDsl.SuperRamp] objects are
 * structurally equal. One `shouldBe` validates the whole binding chain (field, order, coercion, defaults).
 */
class KlangScriptSuperRampSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun superRamp() = KlangScriptOsc.superramp()

    "Osc.superramp() — KlangScript == Kotlin builder" {
        ks("Osc.superramp()") shouldBe superRamp()
    }

    "freq(220)" {
        ks("Osc.superramp().freq(220)") shouldBe superRamp().copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(9)" {
        ks("Osc.superramp().voices(9)") shouldBe superRamp().copy(voices = IgnitorDsl.Constant(9.0))
    }

    "spread(0.3)" {
        ks("Osc.superramp().spread(0.3)") shouldBe superRamp().copy(spread = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.superramp().analog(5.0)") shouldBe superRamp().copy(analog = IgnitorDsl.Constant(5.0))
    }

    "spreadPower(1.5)" {
        ks("Osc.superramp().spreadPower(1.5)") shouldBe superRamp().copy(spreadPower = 1.5)
    }

    "sideAtten(0.25)" {
        ks("Osc.superramp().sideAtten(0.25)") shouldBe superRamp().copy(sideAtten = 0.25)
    }

    "gainJitter(0.0)" {
        ks("Osc.superramp().gainJitter(0.0)") shouldBe superRamp().copy(gainJitter = 0.0)
    }

    "centerJitter(1.0) maps to centerJitterScale" {
        ks("Osc.superramp().centerJitter(1.0)") shouldBe superRamp().copy(centerJitterScale = 1.0)
    }

    "every typed config method in one chain" {
        val code = "Osc.superramp().freq(110).voices(11).spread(0.12).analog(4.0)" +
                ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)"

        ks(code) shouldBe superRamp().copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(11.0),
            spread = IgnitorDsl.Constant(0.12),
            analog = IgnitorDsl.Constant(4.0),
            spreadPower = 1.4,
            sideAtten = 0.2,
            gainJitter = 0.1,
            centerJitterScale = 0.6,
        )
    }

    "superramp().lowpass(2000) — base wrapper applies to the narrowed subtype" {
        val dsl = ks("Osc.superramp().spreadPower(1.5).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperRamp>()
        inner.spreadPower shouldBe 1.5
    }
})
