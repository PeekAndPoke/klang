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
            "pattern.room(amount)" to note("c").room(0.5),
            "script pattern.room(amount)" to SprudelPattern.compile("""note("c").room(0.5)"""),
            "string.room(amount)" to "c".room(0.5),
            "script string.room(amount)" to SprudelPattern.compile(""""c".room(0.5)"""),
            "room(amount)" to note("c").apply(room(0.5)),
            "script room(amount)" to SprudelPattern.compile("""note("c").apply(room(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as room | seq(\"0 0.5\").room()" {
        val p = seq("0 0.5").room()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "reinterpret voice data as room | \"0 0.5\".room()" {
        val p = "0 0.5".room()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "reinterpret voice data as room | seq(\"0 0.5\").apply(room())" {
        val p = seq("0 0.5").apply(room())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.room shouldBe 0.0
            events[1].data.room shouldBe 0.5
        }
    }

    "room() sets VoiceData.room" {
        val p = note("a b").room("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.room } shouldBe listOf(0.5, 0.8)
    }

    "room() works as pattern extension" {
        val p = note("c").room("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "room() works as string extension" {
        val p = "c".room("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    "room() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").room("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.room shouldBe 0.5
    }

    // ── Colon-separated reverb params via room() ──────────────────────

    "room() parses colon-separated string setting all reverb params" {
        val p = note("c").room("0.5:2:0.3:4000:2000")
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

    "room() parses partial colon-separated string (only room and size)" {
        val p = note("c").room("0.8:4")
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

    "room() still works with a plain number" {
        val p = note("c").room(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.room shouldBe (0.6 plusOrMinus EPSILON)
    }

    "room() with sequenced colon-separated values" {
        val p = note("c c").room("0.3:1 0.8:4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        assertSoftly {
            events[0].data.room shouldBe 0.3
            events[0].data.roomSize shouldBe 1.0
            events[1].data.room shouldBe 0.8
            events[1].data.roomSize shouldBe 4.0
        }
    }

    "room() with continuous pattern sets room correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").room(sine)
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
})
