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
 * Dual-language equivalence for the typed supersine DSL (Phase 2). Mirrors [KlangScriptSuperSawSpec].
 */
class KlangScriptSuperSineSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun superSine() = KlangScriptOsc.supersine()

    "Osc.supersine() — KlangScript == Kotlin builder" {
        ks("Osc.supersine()") shouldBe superSine()
    }

    "freq(220)" {
        ks("Osc.supersine().freq(220)") shouldBe superSine().copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(9)" {
        ks("Osc.supersine().voices(9)") shouldBe superSine().copy(voices = IgnitorDsl.Constant(9.0))
    }

    "spread(0.3)" {
        ks("Osc.supersine().spread(0.3)") shouldBe superSine().copy(spread = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.supersine().analog(5.0)") shouldBe superSine().copy(analog = IgnitorDsl.Constant(5.0))
    }

    "spreadPower(1.5)" {
        ks("Osc.supersine().spreadPower(1.5)") shouldBe superSine().copy(spreadPower = 1.5)
    }

    "sideAtten(0.25)" {
        ks("Osc.supersine().sideAtten(0.25)") shouldBe superSine().copy(sideAtten = 0.25)
    }

    "gainJitter(0.0)" {
        ks("Osc.supersine().gainJitter(0.0)") shouldBe superSine().copy(gainJitter = 0.0)
    }

    "centerJitter(1.0) maps to centerJitterScale" {
        ks("Osc.supersine().centerJitter(1.0)") shouldBe superSine().copy(centerJitterScale = 1.0)
    }

    "every typed config method in one chain" {
        val code = "Osc.supersine().freq(110).voices(11).spread(0.12).analog(4.0)" +
                ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)"

        ks(code) shouldBe superSine().copy(
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

    "supersine().lowpass(2000) — base wrapper applies to the narrowed subtype" {
        val dsl = ks("Osc.supersine().spreadPower(1.5).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperSine>()
        inner.spreadPower shouldBe 1.5
    }
})
