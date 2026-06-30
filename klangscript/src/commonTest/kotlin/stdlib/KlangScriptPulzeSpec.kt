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
 * Dual-language equivalence for the typed pulse shape DSL (Phase 2). `Osc.square()` returns
 * [IgnitorDsl.Pulze]. Mirrors [KlangScriptSawtoothSpec].
 */
class KlangScriptPulzeSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun pulze() = KlangScriptOsc.square()

    "Osc.square() — KlangScript == Kotlin builder" {
        ks("Osc.square()") shouldBe pulze()
    }

    "freq(220)" {
        ks("Osc.square().freq(220)") shouldBe pulze().copy(freq = IgnitorDsl.Constant(220.0))
    }

    "duty(0.3)" {
        ks("Osc.square().duty(0.3)") shouldBe pulze().copy(duty = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.square().analog(5.0)") shouldBe pulze().copy(analog = IgnitorDsl.Constant(5.0))
    }

    "flankSamples(4.0)" {
        ks("Osc.square().flankSamples(4.0)") shouldBe pulze().copy(flankSamples = 4.0)
    }

    "riseFlank(0.5)" {
        ks("Osc.square().riseFlank(0.5)") shouldBe pulze().copy(riseFlank = 0.5)
    }

    "fallFlank(0.5)" {
        ks("Osc.square().fallFlank(0.5)") shouldBe pulze().copy(fallFlank = 0.5)
    }

    "every typed config method in one chain" {
        val code = "Osc.square().freq(110).duty(0.4).analog(4.0)" +
                ".flankSamples(3.0).riseFlank(0.2).fallFlank(0.3)"

        ks(code) shouldBe pulze().copy(
            freq = IgnitorDsl.Constant(110.0),
            duty = IgnitorDsl.Constant(0.4),
            analog = IgnitorDsl.Constant(4.0),
            flankSamples = 3.0,
            riseFlank = 0.2,
            fallFlank = 0.3,
        )
    }

    "square().lowpass(2000) — base wrapper applies to the narrowed subtype" {
        val dsl = ks("Osc.square().flankSamples(4.0).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.Pulze>()
        inner.flankSamples shouldBe 4.0
    }
})
