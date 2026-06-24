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
 * Integration tests for the OscSlot DSL in KlangScript.
 *
 * Each OscSlot.* property returns the matching canonical [IgnitorDsl.Param]
 * singleton that built-in sounds wire in for sprudel modulation compatibility.
 */
class KlangScriptOscSlotTest : StringSpec({

    fun evalIgnitorDsl(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        val value = result.value
        value.shouldBeInstanceOf<IgnitorDsl>()
        return value
    }

    "OscSlot.analog → Param(\"analog\", 0.0)" {
        evalIgnitorDsl("OscSlot.analog") shouldBe IgnitorDsl.Param("analog", 0.0)
    }

    "OscSlot.voices → Param(\"voices\", 8.0)" {
        evalIgnitorDsl("OscSlot.voices") shouldBe IgnitorDsl.Param("voices", 8.0)
    }

    "OscSlot.freqSpread → Param(\"freqSpread\", 0.2)" {
        evalIgnitorDsl("OscSlot.freqSpread") shouldBe IgnitorDsl.Param("freqSpread", 0.2)
    }

    "OscSlot.duty → Param(\"duty\", 0.5)" {
        evalIgnitorDsl("OscSlot.duty") shouldBe IgnitorDsl.Param("duty", 0.5)
    }

    "OscSlot.density → Param(\"density\", 0.2)" {
        evalIgnitorDsl("OscSlot.density") shouldBe IgnitorDsl.Param("density", 0.2)
    }

    "OscSlot.decay → Param(\"decay\", 0.996)" {
        evalIgnitorDsl("OscSlot.decay") shouldBe IgnitorDsl.Param("decay", 0.996)
    }

    "OscSlot.brightness → Param(\"brightness\", 0.5)" {
        evalIgnitorDsl("OscSlot.brightness") shouldBe IgnitorDsl.Param("brightness", 0.5)
    }

    "OscSlot.pickPosition → Param(\"pickPosition\", 0.5)" {
        evalIgnitorDsl("OscSlot.pickPosition") shouldBe IgnitorDsl.Param("pickPosition", 0.5)
    }

    "OscSlot.stiffness → Param(\"stiffness\", 0.0)" {
        evalIgnitorDsl("OscSlot.stiffness") shouldBe IgnitorDsl.Param("stiffness", 0.0)
    }

    "OscSlot.rate → Param(\"rate\", 1.0)" {
        evalIgnitorDsl("OscSlot.rate") shouldBe IgnitorDsl.Param("rate", 1.0)
    }

    "Osc.slot.analog (member-property chain) → Param(\"analog\", 0.0)" {
        evalIgnitorDsl("Osc.slot.analog") shouldBe IgnitorDsl.Param("analog", 0.0)
    }

    "Osc.sine().analog(OscSlot.analog) opens the analog slot on a custom sound" {
        val dsl = evalIgnitorDsl("Osc.sine().analog(OscSlot.analog)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.analog shouldBe IgnitorDsl.Param("analog", 0.0)
    }
})
