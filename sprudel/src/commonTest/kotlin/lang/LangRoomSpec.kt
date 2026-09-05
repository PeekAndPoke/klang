/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangRoomSpec : StringSpec({

    "room dsl interface" {
        dslInterfaceTests(
            "pattern.roomWet(amount)" to note("c").roomWet(0.5),
            "script pattern.roomWet(amount)" to SprudelPattern.compile("""note("c").roomWet(0.5)"""),
            "string.roomWet(amount)" to "c".roomWet(0.5),
            "script string.roomWet(amount)" to SprudelPattern.compile(""""c".roomWet(0.5)"""),
            "roomWet(amount)" to note("c").apply(roomWet(0.5)),
            "script roomWet(amount)" to SprudelPattern.compile("""note("c").apply(roomWet(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as room | seq(\"0 0.5\").roomWet()" {
        val p = seq("0 0.5").roomWet()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "reinterpret voice data as room | \"0 0.5\".roomWet()" {
        val p = "0 0.5".roomWet()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "reinterpret voice data as room | seq(\"0 0.5\").apply(roomWet())" {
        val p = seq("0 0.5").apply(roomWet())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "roomWet() sets VoiceData.room" {
        val p = note("a b").roomWet("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.5, 0.8)
    }

    "roomWet() works as pattern extension" {
        val p = note("c").roomWet("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "roomWet() works as string extension" {
        val p = "c".roomWet("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "roomWet() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").roomWet("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    // ── Per-param reverb params via roomWet() ───────────────────────────

    "roomWet() per-param sets all reverb params" {
        val p = note("c").roomWet(0.5, 2, 0.3, 4000, 2000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.room shouldBe 0.5
            events[0].data.roomSize shouldBe 2.0
            events[0].data.roomFade shouldBe 0.3
            events[0].data.roomLp shouldBe 4000.0
            events[0].data.roomDim shouldBe 2000.0
        }
    }

    "roomWet() with leading params sets only room and size" {
        val p = note("c").roomWet(0.8, 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.room shouldBe 0.8
            events[0].data.roomSize shouldBe 4.0
            events[0].data.roomFade shouldBe null // not specified
            events[0].data.roomLp shouldBe null
            events[0].data.roomDim shouldBe null
        }
    }

    "roomWet() still works with a plain number" {
        val p = note("c").roomWet(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe (0.6 plusOrMinus EPSILON)
    }

    "roomWet() with per-param sequenced values" {
        val p = note("c c").roomWet("0.3 0.8", "1 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        assertSoftly {
            events[0].data.room shouldBe 0.3
            events[0].data.roomSize shouldBe 1.0
            events[1].data.room shouldBe 0.8
            events[1].data.roomSize shouldBe 4.0
        }
    }

    "roomWet() with continuous pattern sets room correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").roomWet(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.room shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.room shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.room shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.room shouldBe (0.0 plusOrMinus EPSILON)
    }

    "roomWet(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the room field
        val p = SprudelPattern.compile("""seq("3 4").roomWet(size = 8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.room shouldBe null
        events[0].data.roomSize shouldBe 8.0
    }
})
