/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * `reverb(wet, size, lowpass)`, the orbit reverb: the blueprint of the compound-door fill (`/dsl-design` section 4).
 * The calling forms and the positional order are `LangDoorFormsSpec`'s. `reverb` replaced `room` on 2026-09-16
 * (`docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`).
 */
class LangReverbSpec : StringSpec({

    // -- the fill ----------------------------------------------------------------------------------------------------------

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
})
