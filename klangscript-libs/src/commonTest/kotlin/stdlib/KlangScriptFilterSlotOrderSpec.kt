/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The filter doors' positional slots. Until phase 3 step 3d(i), `passes` was the THIRD slot
 * (C5, 2026-08-24) and `analog` the fourth, a trap against sprudel's `lpf(freq, q, passes, env)`
 * where a fourth number is an envelope depth. Since 3d(i) the third slot is the `configure`
 * lambda and every secondary knob lives on the builder, so a third NUMBER is refused instead of
 * silently meaning something else. What C5 also pinned still holds and is pinned here: a
 * fractional cascade count ROUNDS like sprudel's `coercePasses`, inside the 1..16 ceiling.
 */
class KlangScriptFilterSlotOrderSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    "the third slot is the lambda: a number there is refused on all four filters" {
        for (door in listOf("lowpass", "highpass", "bandpass", "notch")) {
            shouldThrowAny { ks("""Osc.saw().$door(800, 1.8, 3)""") }
        }
    }

    "passes and analog reach the node through the builder, and analog stays clean without it" {
        val lp = ks("""Osc.saw().lowpass(800, 1.8, x => x.passes(3))""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 3
        lp.freq shouldBe IgnitorDsl.Constant(800.0)
        lp.q shouldBe IgnitorDsl.Constant(1.8)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)

        val hp = ks("""Osc.saw().highpass(200, 0.9, x => x.passes(2).analog(4))""") as IgnitorDsl.Highpass
        hp.passes shouldBe 2
        hp.analog shouldBe IgnitorDsl.Constant(4.0)
    }

    "the lambda floats past an omitted q" {
        val lp = ks("""Osc.saw().lowpass(800, x => x.analog(4))""") as IgnitorDsl.Lowpass
        lp.q shouldBe IgnitorDsl.Constant(0.707)
        lp.analog shouldBe IgnitorDsl.Constant(4.0)
    }

    "fractional counts ROUND on this door too — every number in KlangScript is a double" {
        // Cross-door parity (round 3 of C5): sprudel rounds via coercePasses; an `Int` parameter
        // would TRUNCATE. `0.3 * 10` is 2.9999999999999996, so the same expression would build a
        // 24 dB/oct filter here and a 36 dB/oct one in sprudel.
        (ks("""Osc.saw().lowpass(800, 1.0, x => x.passes(0.3 * 10))""") as IgnitorDsl.Lowpass).passes shouldBe 3
        (ks("""Osc.saw().lowpass(800, 1.0, x => x.passes(2.4))""") as IgnitorDsl.Lowpass).passes shouldBe 2
        (ks("""Osc.saw().highpass(200, 1.0, x => x.passes(2.7))""") as IgnitorDsl.Highpass).passes shouldBe 3
        // ...and the same 1..16 resource ceiling, from the same single place.
        (ks("""Osc.saw().lowpass(800, 1.0, x => x.passes(1000000))""") as IgnitorDsl.Lowpass).passes shouldBe 16
        (ks("""Osc.saw().lowpass(800, 1.0, x => x.passes(0))""") as IgnitorDsl.Lowpass).passes shouldBe 1
    }

    "bare defaults: one stage, no analog" {
        val lp = ks("""Osc.saw().lowpass(800)""") as IgnitorDsl.Lowpass
        lp.passes shouldBe 1
        lp.q shouldBe IgnitorDsl.Constant(0.707)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)
    }
})
