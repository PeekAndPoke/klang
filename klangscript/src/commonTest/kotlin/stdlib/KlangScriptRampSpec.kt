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
 * Dual-language equivalence for the typed ramp shape DSL (Phase 2). Mirrors [KlangScriptSawtoothSpec].
 */
class KlangScriptRampSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun ramp() = KlangScriptOsc.ramp()

    "Osc.ramp() — KlangScript == Kotlin builder" {
        ks("Osc.ramp()") shouldBe ramp()
    }

    "freq(220)" {
        ks("Osc.ramp().freq(220)") shouldBe ramp().copy(freq = IgnitorDsl.Constant(220.0))
    }

    "analog(5.0)" {
        ks("Osc.ramp().analog(5.0)") shouldBe ramp().copy(analog = IgnitorDsl.Constant(5.0))
    }

    "resetSamples(4.0)" {
        ks("Osc.ramp().resetSamples(4.0)") shouldBe ramp().copy(resetSamples = 4.0)
    }

    "shapeMax(0.3)" {
        ks("Osc.ramp().shapeMax(0.3)") shouldBe ramp().copy(shapeMax = 0.3)
    }

    "every typed config method in one chain" {
        ks("Osc.ramp().freq(110).analog(4.0).resetSamples(3.0).shapeMax(0.4)") shouldBe ramp().copy(
            freq = IgnitorDsl.Constant(110.0),
            analog = IgnitorDsl.Constant(4.0),
            resetSamples = 3.0,
            shapeMax = 0.4,
        )
    }

    "ramp().lowpass(2000) — base wrapper applies to the narrowed subtype" {
        val dsl = ks("Osc.ramp().resetSamples(4.0).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.Ramp>()
        inner.resetSamples shouldBe 4.0
    }
})
