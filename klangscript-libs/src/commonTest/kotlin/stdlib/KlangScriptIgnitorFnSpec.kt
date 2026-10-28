/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/** `Ign.fn(f)`: the function handed back as it is, a stage that plays as the bare lambda does, both doors. */
class KlangScriptIgnitorFnSpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    "a stage made with Ign.fn plays as the bare lambda, in serial and called by itself" {
        val expected = ignitor("Ignitor.saw().mul(2).plus(3)")

        ignitor("let neck = Ign.fn(x => x.mul(2).plus(3))\nIgnitor.saw().serial(neck)") shouldBe expected
        ignitor("let neck = x => x.mul(2).plus(3)\nIgnitor.saw().serial(neck)") shouldBe expected
        ignitor("let neck = Ign.fn(x => x.mul(2).plus(3))\nneck(Ignitor.saw())") shouldBe expected
    }

    "the Kotlin door hands the function back as it is" {
        val f: (IgnitorDsl) -> IgnitorDsl = { it }

        Ign.fn(f) shouldBeSameInstanceAs f
    }
})
