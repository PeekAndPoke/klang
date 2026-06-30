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
 * Dual-language equivalence for the chaotic crackle generator's `chaos` knob.
 * `crackle` is no longer a dust alias — it drives the SuperCollider Crackle map; `chaos` replaces the
 * former `density` param.
 */
class KlangScriptCrackleSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun crackle() = KlangScriptOsc.crackle() as IgnitorDsl.Crackle

    "Osc.crackle() — KlangScript == Kotlin builder" {
        ks("Osc.crackle()") shouldBe crackle()
    }

    "Osc.crackle(1.8) — positional chaos" {
        ks("Osc.crackle(1.8)") shouldBe crackle().copy(chaos = IgnitorDsl.Constant(1.8))
    }

    "Osc.crackle(chaos = 1.2) — named chaos" {
        ks("Osc.crackle(chaos = 1.2)") shouldBe crackle().copy(chaos = IgnitorDsl.Constant(1.2))
    }
})
