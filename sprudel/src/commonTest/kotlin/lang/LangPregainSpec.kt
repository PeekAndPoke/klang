/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * The `pregain` door: how hard an event is played INTO its instrument (the signal-flow plan,
 * section 6, spot A).
 *
 * It is exactly `oscparam("pregain", x)` and nothing else, which is the property most of these
 * rows are about: it writes ONE key of the oscParams bag, it leaves `gain` alone, and a
 * hand-written `oscp("pregain", x)` is the same event. What the slot then DOES is the
 * instrument's business and is guarded in audio_be (`PregainSlotRenderSpec`,
 * `VoicePregainWireSpec`); nothing here can hear anything.
 */
class LangPregainSpec : StringSpec({

    "pregain dsl interface" {
        val pat = "0 1"
        val ctrl = "1 0.7"

        dslInterfaceTests(
            "pattern.pregain(ctrl)" to
                    seq(pat).pregain(ctrl),
            "script pattern.pregain(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").pregain("$ctrl")"""),
            "string.pregain(ctrl)" to
                    pat.pregain(ctrl),
            "script string.pregain(ctrl)" to
                    SprudelPattern.compile(""""$pat".pregain("$ctrl")"""),
            "pregain(ctrl)" to
                    seq(pat).apply(pregain(ctrl)),
            "script pregain(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(pregain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("pregain") shouldBe 1.0
            events[1].data.oscParams?.get("pregain") shouldBe 0.7
        }
    }

    "the chained mapper form applies after the previous mapper" {
        val p = seq("0 1").apply(gain(0.4).pregain("1 0.7"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.gain shouldBe 0.4
            events[0].data.oscParams?.get("pregain") shouldBe 1.0
            events[1].data.oscParams?.get("pregain") shouldBe 0.7
        }
    }

    "pregain() with no argument reinterprets the pattern's own value" {
        val events = seq("1 0.7").pregain().queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("pregain") shouldBe 1.0
            events[1].data.oscParams?.get("pregain") shouldBe 0.7
        }
    }

    "pregain IS oscparam(\"pregain\", ...): the same slot, written the long way" {
        val door = note("c3 e3").pregain("1 0.5").queryArc(0.0, 1.0)
        val raw = note("c3 e3").oscp("pregain", "1 0.5").queryArc(0.0, 1.0)

        door.map { it.data.oscParams?.get("pregain") } shouldBe listOf(1.0, 0.5)
        door.map { it.data.oscParams } shouldBe raw.map { it.data.oscParams }
    }

    "pregain does not touch gain, and gain does not touch pregain" {
        // The two level words are separate fields on separate hosts: `gain` is a voice field,
        // `pregain` a slot in the oscParams bag. A door that wrote the wrong one would still
        // "work" in a render, which is why this is asserted rather than assumed.
        val events = note("c3").gain(0.4).pregain(2.0).queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.gain shouldBe 0.4
            events[0].data.oscParams?.get("pregain") shouldBe 2.0
            events[0].data.oscParams?.get("gain") shouldBe null
        }
    }

    "a later pregain REPLACES an earlier one; a mapper scales it" {
        val replaced = note("c3").pregain(2.0).pregain(0.5).queryArc(0.0, 1.0)
        val scaled = note("c3").pregain(2.0).pregain(mul(0.5)).queryArc(0.0, 1.0)

        replaced[0].data.oscParams?.get("pregain") shouldBe 0.5
        scaled[0].data.oscParams?.get("pregain") shouldBe 1.0
    }

    "a mapper on an UNSET pregain does nothing, the general mapper rule" {
        // `_mapNumericField` reads the slot into the value register, and there is nothing to
        // read, so nothing is written back. Recorded rather than fixed: the same is true of
        // `gain(mul(x))` (the signal-flow plan, section 6, the note parked for the maintainer).
        val events = note("c3").pregain(mul(0.5)).queryArc(0.0, 1.0)

        events[0].data.oscParams?.get("pregain") shouldBe null
    }

    "unset is unset: no door, no key in the bag" {
        val events = note("c3").s("supersaw").queryArc(0.0, 1.0)

        events[0].data.oscParams?.get("pregain") shouldBe null
    }
})
