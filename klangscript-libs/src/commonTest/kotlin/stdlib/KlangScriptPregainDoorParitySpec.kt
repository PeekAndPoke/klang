/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pregain
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The two doors of the `pregain` slot: `OscSlot.pregain` / `.pregain()` in KlangScript and
 * `IgnitorDsl.Slots.pregain` / `.pregain()` from Kotlin (the dual-surface rule, `/dsl-design`
 * section 3).
 *
 * The claim under test is stronger than "both compile": both must build the SAME node, so an
 * instrument written in a song and the same instrument written in Kotlin are one entry in the
 * identity map. And `.pregain()` must be the same tree as the spelled-out `.mul(OscSlot.pregain)`
 * on either door, because that equivalence is what the helper's KDoc promises on both.
 */
class KlangScriptPregainDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    "OscSlot.pregain is the canonical slot, on both spellings of the script door" {
        ks("OscSlot.pregain") shouldBe IgnitorDsl.Slots.pregain
        ks("Osc.slot.pregain") shouldBe IgnitorDsl.Slots.pregain
        ks("OscSlot.pregain") shouldBe IgnitorDsl.Param("pregain", 1.0)
    }

    "the script helper builds the Kotlin helper's tree, node for node" {
        val script = ks("Osc.saw().pregain()")
        val kotlin = IgnitorDsl.Sawtooth().pregain()

        script shouldBe kotlin

        // ...and it really is the multiply, with the signal on the left on both doors.
        val times = script.shouldBeInstanceOf<IgnitorDsl.Times>()

        times.left shouldBe IgnitorDsl.Sawtooth()
        times.right shouldBe IgnitorDsl.Slots.pregain
    }

    "on each door the helper equals the spelled-out mul, and the doors agree with each other" {
        ks("Osc.saw().pregain()") shouldBe ks("Osc.saw().mul(OscSlot.pregain)")
        IgnitorDsl.Sawtooth().pregain() shouldBe IgnitorDsl.Sawtooth().mul(IgnitorDsl.Slots.pregain)
        ks("Osc.saw().mul(OscSlot.pregain)") shouldBe IgnitorDsl.Sawtooth().mul(IgnitorDsl.Slots.pregain)
    }

    "a whole driven instrument is the same tree on both doors" {
        // The shape the built-ins take in phase 3 and the one the KDoc examples show: the slot in
        // front of the nonlinearity, not at the end. The drive is the examples' `0.5` and not
        // `2.0`, where the shaper saturates and the slot is inaudible: a tree comparison passes at
        // either, but a spec is where a reader copies an instrument from.
        val script = ks("Osc.saw().pregain().distort(0.5)")
        val kotlin = IgnitorDsl.Sawtooth().pregain().distort(0.5)

        script shouldBe kotlin

        withClue("the slot sits INSIDE the distortion, which is the whole point of the shape") {
            script shouldNotBe IgnitorDsl.Sawtooth().distort(0.5).pregain()
        }
    }
})
