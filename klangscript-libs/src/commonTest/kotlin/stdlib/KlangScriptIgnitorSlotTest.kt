/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
 * Integration tests for the Ignitor.slot DSL in KlangScript.
 *
 * Each Ignitor.slot.* property returns the matching canonical [IgnitorDsl.Param]
 * singleton that built-in sounds wire in for sprudel modulation compatibility.
 */
class KlangScriptIgnitorSlotTest : StringSpec({

    fun evalIgnitorDsl(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        val value = result.value
        value.shouldBeInstanceOf<IgnitorDsl>()
        return value
    }

    "Ignitor.slot.analog → Param(\"analog\", 0.0)" {
        evalIgnitorDsl("Ignitor.slot.analog") shouldBe IgnitorDsl.Param("analog", 0.0)
    }

    "Ignitor.slot.voices → Param(\"voices\", 8.0)" {
        evalIgnitorDsl("Ignitor.slot.voices") shouldBe IgnitorDsl.Param("voices", 8.0)
    }

    "Ignitor.slot.spread → Param(\"spread\", 0.2)" {
        evalIgnitorDsl("Ignitor.slot.spread") shouldBe IgnitorDsl.Param("spread", 0.2)
    }

    "Ignitor.slot.duty → Param(\"duty\", 0.5)" {
        evalIgnitorDsl("Ignitor.slot.duty") shouldBe IgnitorDsl.Param("duty", 0.5)
    }

    "Ignitor.slot.density → Param(\"density\", 0.2)" {
        evalIgnitorDsl("Ignitor.slot.density") shouldBe IgnitorDsl.Param("density", 0.2)
    }

    "Ignitor.slot.feedback → Param(\"feedback\", 0.996)" {
        evalIgnitorDsl("Ignitor.slot.feedback") shouldBe IgnitorDsl.Param("feedback", 0.996)
    }

    "Ignitor.slot.brightness → Param(\"brightness\", 0.5)" {
        evalIgnitorDsl("Ignitor.slot.brightness") shouldBe IgnitorDsl.Param("brightness", 0.5)
    }

    "Ignitor.slot.pickPosition → Param(\"pickPosition\", 0.5)" {
        evalIgnitorDsl("Ignitor.slot.pickPosition") shouldBe IgnitorDsl.Param("pickPosition", 0.5)
    }

    "Ignitor.slot.stiffness → Param(\"stiffness\", 0.0)" {
        evalIgnitorDsl("Ignitor.slot.stiffness") shouldBe IgnitorDsl.Param("stiffness", 0.0)
    }

    "Ignitor.slot.rate → Param(\"rate\", 1.0)" {
        evalIgnitorDsl("Ignitor.slot.rate") shouldBe IgnitorDsl.Param("rate", 1.0)
    }

    "Ignitor.slot.pregain → Param(\"pregain\", 1.0)" {
        evalIgnitorDsl("Ignitor.slot.pregain") shouldBe IgnitorDsl.Param("pregain", 1.0)
    }

    "Ign.slot.analog (the short name) → Param(\"analog\", 0.0)" {
        evalIgnitorDsl("Ign.slot.analog") shouldBe IgnitorDsl.Param("analog", 0.0)
    }

    "Ignitor.sine(x => x.analog(Ignitor.slot.analog)) opens the analog slot on a custom sound" {
        val dsl = evalIgnitorDsl("Ignitor.sine(x => x.analog(Ignitor.slot.analog))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.analog shouldBe IgnitorDsl.Param("analog", 0.0)
    }
})
