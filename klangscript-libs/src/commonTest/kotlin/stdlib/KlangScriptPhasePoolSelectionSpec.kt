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
 * `selection` surface pin (2026-08-24): a STRING on the script door, default `"normal"`,
 * the value-colon compound `"name[:width[:outliers]]"` passed through VERBATIM to the wire
 * (parsed at voice build). Round-robin — the old default — is opt-in.
 */
class KlangScriptPhasePoolSelectionSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    "node default is normal — nothing set means the guitar-like mode" {
        IgnitorDsl.SuperSaw(freq = IgnitorDsl.Constant(220.0)).selection shouldBe "normal"
    }

    "bare .phasePool() keeps the normal default" {
        val saw = ks("""Osc.supersaw(x => x.phasePool())""") as IgnitorDsl.SuperSaw
        saw.selection shouldBe "normal"
    }

    "the compound form travels verbatim: selection = \"normal:0.1:0.9\"" {
        val saw = ks("""Osc.supersaw(x => x.phasePool(selection = "normal:0.1:0.9"))""") as IgnitorDsl.SuperSaw
        saw.selection shouldBe "normal:0.1:0.9"
    }

    "roundrobin is expressible — but only as an explicit opt-in" {
        val saw = ks("""Osc.supersaw(x => x.phasePool(selection = "roundrobin"))""") as IgnitorDsl.SuperSaw
        saw.selection shouldBe "roundrobin"
    }
})
