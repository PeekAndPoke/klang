/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * `reverb(wet, size, lowpass)`, the orbit reverb. The size slot has its own
 * [LangReverbSizeSpec]. `reverb` replaced `room` on 2026-09-16 (`docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`).
 */
class LangReverbSpec : StringSpec({

    // -- reverb(wet) ------------------------------------------------------------------------------------------------------

    "reverb dsl interface" {
        dslInterfaceTests(
            "pattern.reverb(amount)" to note("c").reverb(0.5),
            "script pattern.reverb(amount)" to SprudelPattern.compile("""note("c").reverb(0.5)"""),
            "string.reverb(amount)" to "c".reverb(0.5),
            "script string.reverb(amount)" to SprudelPattern.compile(""""c".reverb(0.5)"""),
            "reverb(amount)" to note("c").apply(reverb(0.5)),
            "script reverb(amount)" to SprudelPattern.compile("""note("c").apply(reverb(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as the reverb send | seq(\"0 0.5\").reverb()" {
        val p = seq("0 0.5").reverb()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.0
            events[1].data.katalystParams?.get("reverb.wet") shouldBe 0.5
        }
    }

    "reinterpret voice data as the reverb send | \"0 0.5\".reverb()" {
        val p = "0 0.5".reverb()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.0
            events[1].data.katalystParams?.get("reverb.wet") shouldBe 0.5
        }
    }

    "reinterpret voice data as the reverb send | seq(\"0 0.5\").apply(reverb())" {
        val p = seq("0 0.5").apply(reverb())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.0
            events[1].data.katalystParams?.get("reverb.wet") shouldBe 0.5
        }
    }

    "reverb() sets the reverb.wet slot" {
        val p = note("a b").reverb("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.katalystParams?.get("reverb.wet") } shouldBe listOf(0.5, 0.8)
    }

    "reverb() works as string extension" {
        val p = "c".reverb("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.5
    }

    "reverb() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").reverb("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.5
    }

    "reverb() with a continuous pattern sets the send" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").reverb(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.katalystParams?.get("reverb.wet") shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.katalystParams?.get("reverb.wet") shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.katalystParams?.get("reverb.wet") shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.katalystParams?.get("reverb.wet") shouldBe (0.0 plusOrMinus EPSILON)
    }

    // -- positional slots -------------------------------------------------------------------------------------------------

    "reverb(wet, size, lowpass) sets all three slots, in that order" {
        val p = note("c").reverb(0.5, 2, 4000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.5
            events[0].data.katalystParams?.get("reverb.size") shouldBe 2.0
            events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 4000.0
        }
    }

    "the third positional slot is lowpass in compiled code too" {
        // `room(wet, size, fade)` had fade third; a migrated positional call must not silently
        // land a fade value in the lowpass.
        val p = SprudelPattern.compile("""note("c").reverb(0.8, 2, 3000)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        assertSoftly {
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.8
            events[0].data.katalystParams?.get("reverb.size") shouldBe 2.0
            events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 3000.0
        }
    }

    "reverb() with leading params sets only wet and size" {
        val p = note("c").reverb(0.8, 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.8
            events[0].data.katalystParams?.get("reverb.size") shouldBe 4.0
            events[0].data.katalystParams?.get("reverb.lowpass") shouldBe null
        }
    }

    "reverb() with a single param sets the send, and the other slots take their defaults" {
        val p = note("c").reverb(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            katalystParams?.get("reverb.wet") shouldBe (0.6 plusOrMinus EPSILON)
            katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
            katalystParams?.get("reverb.lowpass") shouldBe null // no default
        }
    }

    "reverb() with per-param sequenced values" {
        val p = note("c c").reverb("0.3 0.8", "1 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        assertSoftly {
            events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.3
            events[0].data.katalystParams?.get("reverb.size") shouldBe 1.0
            events[1].data.katalystParams?.get("reverb.wet") shouldBe 0.8
            events[1].data.katalystParams?.get("reverb.size") shouldBe 4.0
        }
    }

    "reverb() works with mini-notation patterns" {
        val p = note("c3 e3").reverb("<0.3 0.8>", "<1 4>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.katalystParams?.get("reverb.wet") shouldBe 0.3
            cycle0[0].data.katalystParams?.get("reverb.size") shouldBe 1.0

            cycle1.size shouldBe 2
            cycle1[0].data.katalystParams?.get("reverb.wet") shouldBe 0.8
            cycle1[0].data.katalystParams?.get("reverb.size") shouldBe 4.0
        }
    }

    "reverb() works chained with other effects" {
        val p = note("c").apply(gain(0.8).reverb(0.5, 2))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            katalystParams?.get("reverb.wet") shouldBe 0.5
            katalystParams?.get("reverb.size") shouldBe 2.0
        }
    }

    "reverb(tail-only) does not reinterpret into the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the send; the send takes its default instead
        val p = SprudelPattern.compile("""seq("3 4").reverb(size = 8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.katalystParams?.get("reverb.wet") shouldBe REVERB_WET
        events[0].data.katalystParams?.get("reverb.size") shouldBe 8.0
    }

    // -- the call sets every slot ----------------------------------------------------------------------------------------

    "reverb(size = ...) alone sends the default, in both doors" {
        listOf(
            note("c").reverb(size = 4),
            SprudelPattern.compile("""note("c").reverb(size = 4)""")!!,
        ).forEach { p ->
            with(p.queryArc(0.0, 1.0)[0].data) {
                katalystParams?.get("reverb.wet") shouldBe REVERB_WET
                katalystParams?.get("reverb.size") shouldBe 4.0
            }
        }
    }

    "every write path fills: lowpass alone, a bare call, the send mapper" {
        with(note("c").reverb(lowpass = 2000).queryArc(0.0, 1.0)[0].data) {
            withClue("lowpass alone") {
                katalystParams?.get("reverb.wet") shouldBe REVERB_WET
                katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
            }
        }
        with(seq("0.3").reverb().queryArc(0.0, 1.0)[0].data) {
            withClue("bare call") {
                katalystParams?.get("reverb.wet") shouldBe 0.3
                katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
            }
        }
        with(SprudelPattern.compile("""s("bd").delay(0.3).reverb(delay.wet)""")!!.queryArc(0.0, 1.0)[0].data) {
            withClue("send from a reader") {
                katalystParams?.get("reverb.wet") shouldBe 0.3
                katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
            }
        }
    }

    "slots apply in order: a mapper on a later slot sees the default an earlier slot filled" {
        note("c").reverb(0.3, size = mul(2)).queryArc(0.0, 1.0)[0].data.katalystParams?.get("reverb.size") shouldBe REVERB_SIZE * 2
    }

    "a slot an earlier call set keeps its value" {
        val data = note("c").reverb(0.3, 4).reverb(lowpass = 2000).queryArc(0.0, 1.0)[0].data

        data.katalystParams?.get("reverb.wet") shouldBe 0.3
        data.katalystParams?.get("reverb.size") shouldBe 4.0
        data.katalystParams?.get("reverb.lowpass") shouldBe 2000.0
    }

    "a rest in a control pattern sets nothing on that event" {
        val p = s("bd").reverb("<0.5 ~>")

        with(p.queryArc(0.0, 1.0)[0].data) {
            katalystParams?.get("reverb.wet") shouldBe 0.5
            katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
        }
        with(p.queryArc(1.0, 2.0)[0].data) {
            katalystParams?.get("reverb.wet").shouldBeNull()
            katalystParams?.get("reverb.size").shouldBeNull()
        }
    }

    "a mapper on a slot that was never set sets nothing" {
        with(s("bd").reverb(size = mul(2)).queryArc(0.0, 1.0)[0].data) {
            katalystParams?.get("reverb.wet").shouldBeNull()
            katalystParams?.get("reverb.size").shouldBeNull()
        }
    }

    "a reader sees the value the call filled in" {
        val data = SprudelPattern.compile("""s("bd").reverb(0.3).pan(reverb.size.div(10))""")!!.queryArc(0.0, 1.0)[0].data

        data.pan shouldBe REVERB_SIZE / 10
    }

    "merge carries the filled defaults of the merged pattern" {
        // Decided 2026-09-16: a filled default is a set value like any other, so merge copies it.
        val data = note("c").reverb(0.5, 8).merge(s("x").reverb(0.2)).queryArc(0.0, 1.0)[0].data

        data.katalystParams?.get("reverb.wet") shouldBe 0.2
        data.katalystParams?.get("reverb.size") shouldBe REVERB_SIZE
    }

    // -- reverb(lowpass = ...) --------------------------------------------------------------------------------------------

    "reverb(lowpass = ...) sets the reverb.lowpass slot correctly" {
        val p = note("c3").reverb(lowpass = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 1000.0
    }

    "reverb(lowpass = ...) works as top-level function" {
        val p = note("a").apply(reverb(lowpass = "500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 500.0
    }

    "reverb(lowpass = ...) works with control pattern" {
        val p = note("c3 e3").reverb(lowpass = "800 1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 800.0
        events[1].data.katalystParams?.get("reverb.lowpass") shouldBe 1200.0
    }

    "reverb(lowpass = ...) works as string extension" {
        val p = "c3".reverb(lowpass = "1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 1500.0
    }

    // -- chaining ---------------------------------------------------------------------------------------------------------

    "reverb slots can be chained together" {
        val p = note("c3")
            .reverb("0.8")
            .reverb(size = "0.9")
            .reverb(lowpass = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.8
        events[0].data.katalystParams?.get("reverb.size") shouldBe 0.9
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 1000.0
    }

    "reverb slots work named in compiled code" {
        val p = SprudelPattern.compile("""note("c3").reverb(wet = 0.8, size = 4, lowpass = 1000)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.katalystParams?.get("reverb.wet") shouldBe 0.8
        events[0].data.katalystParams?.get("reverb.size") shouldBe 4.0
        events[0].data.katalystParams?.get("reverb.lowpass") shouldBe 1000.0
    }
})
