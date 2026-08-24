/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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
 * C5 cross-door slot pin (2026-08-24): `passes` is the THIRD positional slot on the
 * KlangScript door, exactly as it is in sprudel (`lpf(freq, q, passes)`) and on the Kotlin
 * door. `analog` follows it, in fourth.
 *
 * This is the highest-blast-radius line of C5: before the reorder, `.lowpass(800, 1, 2)`
 * built a 24 dB/oct cascade from Kotlin and a saturating single stage from KlangScript.
 * Nothing else pins the order, so re-inserting `analog` at slot 3 would be silent — the
 * only symptom is that patches sound different.
 */
class KlangScriptFilterSlotOrderSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    "lowpass: the third positional argument is passes, and analog stays clean" {
        val lp = ks("""Osc.saw().lowpass(800, 1.8, 3)""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 3
        lp.cutoffHz shouldBe IgnitorDsl.Constant(800.0)
        lp.q shouldBe IgnitorDsl.Constant(1.8)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)
    }

    "highpass: same third slot" {
        val hp = ks("""Osc.saw().highpass(200, 0.9, 2)""") as IgnitorDsl.Highpass
        hp.passes shouldBe 2
        hp.analog shouldBe IgnitorDsl.Constant(0.0)
    }

    "analog is reachable in fourth, positionally" {
        val lp = ks("""Osc.saw().lowpass(800, 1.8, 1, 4)""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 1
        lp.analog shouldBe IgnitorDsl.Constant(4.0)
    }

    "the all-named form is the one to reach for when combining with analog" {
        // KlangScript forbids MIXING positional and named arguments, so `lowpass(800, 1.8,
        // analog = 4)` throws. All-named is the documented shape.
        val lp = ks("""Osc.saw().lowpass(cutoffHz = 800, q = 1.8, analog = 4)""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 1
        lp.analog shouldBe IgnitorDsl.Constant(4.0)
        lp.q shouldBe IgnitorDsl.Constant(1.8)
    }

    "fractional counts ROUND on this door too — every number in KlangScript is a double" {
        // Cross-door parity (round 3): sprudel rounds via coercePasses; the script door used
        // to hand `Int` to the thunk, which TRUNCATES. `0.3 * 10` is 2.9999999999999996, so
        // the same expression built a 24 dB/oct filter here and a 36 dB/oct one in sprudel.
        (ks("""Osc.saw().lowpass(800, 1.0, 0.3 * 10)""") as IgnitorDsl.Lowpass).passes shouldBe 3
        (ks("""Osc.saw().lowpass(800, 1.0, 2.4)""") as IgnitorDsl.Lowpass).passes shouldBe 2
        (ks("""Osc.saw().highpass(200, 1.0, 2.7)""") as IgnitorDsl.Highpass).passes shouldBe 3
        // ...and the same 1..16 resource ceiling, from the same single place.
        (ks("""Osc.saw().lowpass(800, 1.0, 1000000)""") as IgnitorDsl.Lowpass).passes shouldBe 16
        (ks("""Osc.saw().lowpass(800, 1.0, 0)""") as IgnitorDsl.Lowpass).passes shouldBe 1
    }

    "bare defaults: one stage, no analog" {
        val lp = ks("""Osc.saw().lowpass(800)""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 1
        lp.q shouldBe IgnitorDsl.Constant(0.707)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)
    }
})
