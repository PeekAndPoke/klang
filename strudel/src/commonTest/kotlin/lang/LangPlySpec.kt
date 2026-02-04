package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPlySpec : StringSpec({

    "ply() repeats each event n times" {
        val p = note("c d").ply("3")
        val events = p.queryArc(0.0, 1.0)

        // Pattern "c d" has 2 events, each gets repeated 3 times = 6 total
        events.size shouldBe 6

        // First event "c" at [0, 0.5) gets split into 3 parts
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 / 6.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].part.begin.toDouble() shouldBe (1.0 / 6.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (2.0 / 6.0 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[2].part.begin.toDouble() shouldBe (2.0 / 6.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (3.0 / 6.0 plusOrMinus EPSILON)

        // Second event "d" at [0.5, 1.0) gets split into 3 parts
        events[3].data.note shouldBeEqualIgnoringCase "d"
        events[3].part.begin.toDouble() shouldBe (3.0 / 6.0 plusOrMinus EPSILON)
        events[3].part.end.toDouble() shouldBe (4.0 / 6.0 plusOrMinus EPSILON)

        events[4].data.note shouldBeEqualIgnoringCase "d"
        events[4].part.begin.toDouble() shouldBe (4.0 / 6.0 plusOrMinus EPSILON)
        events[4].part.end.toDouble() shouldBe (5.0 / 6.0 plusOrMinus EPSILON)

        events[5].data.note shouldBeEqualIgnoringCase "d"
        events[5].part.begin.toDouble() shouldBe (5.0 / 6.0 plusOrMinus EPSILON)
        events[5].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "ply() with n=2 doubles each event" {
        val p = note("c d e f").ply("2")
        val events = p.queryArc(0.0, 1.0)

        // 4 events, each repeated 2 times = 8 total
        events.size shouldBe 8

        // First event "c" at [0, 0.25) becomes two events
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].part.begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "ply() with n=1 returns original pattern" {
        val original = note("c d e f")
        val plied = note("c d e f").ply("1")

        val originalEvents = original.queryArc(0.0, 1.0)
        val pliedEvents = plied.queryArc(0.0, 1.0)

        pliedEvents.size shouldBe originalEvents.size
        pliedEvents.zip(originalEvents).forEach { (p, o) ->
            p.data.note shouldBeEqualIgnoringCase (o.data.note ?: "")
            p.part.begin shouldBe o.part.begin
            p.part.end shouldBe o.part.end
        }
    }

    "ply(0) with n=0 returns original pattern" {
        val plied = note("c d").ply("0")

        val pliedEvents = plied.queryArc(0.0, 1.0)

        pliedEvents.size shouldBe 0
    }

    "ply() works across multiple cycles" {
        val p = note("c").ply("2")
        val events = p.queryArc(0.0, 2.0)

        // 2 cycles, each with 1 event repeated 2 times = 4 total
        events.size shouldBe 4

        // Cycle 0
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Cycle 1
        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[2].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "c"
        events[3].part.begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[3].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "ply() with high repetition count" {
        val p = note("c").ply("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 8

        // Each repetition should be 1/8 of a cycle
        for (i in 0 until 8) {
            events[i].data.note shouldBeEqualIgnoringCase "c"
            events[i].part.begin.toDouble() shouldBe (i / 8.0 plusOrMinus EPSILON)
            events[i].part.end.toDouble() shouldBe ((i + 1) / 8.0 plusOrMinus EPSILON)
        }
    }

    "ply() works with sound patterns" {
        val p = sound("bd hh").ply("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "bd"
        events[2].data.sound shouldBe "hh"
        events[3].data.sound shouldBe "hh"
    }

    "ply() works as pattern extension" {
        val p = note("c d").ply("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 6
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "ply() works as string extension" {
        val p = note("c d").ply("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[2].data.note shouldBeEqualIgnoringCase "d"
    }

    "ply() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").ply("3")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 6
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "ply() with standalone function syntax" {
        val p = StrudelPattern.compile("""ply("2", note("c d"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[2].data.note shouldBeEqualIgnoringCase "d"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "ply() preserves event data" {
        val p = note("c").gain("0.5").ply("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.forEach { event ->
            event.data.note shouldBeEqualIgnoringCase "c"
            event.data.gain shouldBe 0.5
        }
    }

    "ply() with complex pattern" {
        val p = note("c [d e]").ply("2")
        val events = p.queryArc(0.0, 1.0)

        // Pattern has 3 events: c (0, 0.5), d (0.5, 0.75), e (0.75, 1.0)
        // Each repeated 2 times = 6 total
        events.size shouldBe 6

        // c repeated twice
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "c"

        // d repeated twice
        events[2].data.note shouldBeEqualIgnoringCase "d"
        events[3].data.note shouldBeEqualIgnoringCase "d"

        // e repeated twice
        events[4].data.note shouldBeEqualIgnoringCase "e"
        events[5].data.note shouldBeEqualIgnoringCase "e"
    }

    "ply() with control pattern" {
        val subject = note("c d e f").ply("2 3")

        repeat(12) { cycle ->
            assertSoftly {

                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                val values = events.map {
                    listOf(it.part.begin.toDouble(), it.part.end.toDouble(), it.data.note)
                }

                println("Cycle $cycle | ${events.size} events | $values")

                events.size shouldBe 10
                // c repeated 2 times
                events[0].data.note shouldBeEqualIgnoringCase "c"
                events[1].data.note shouldBeEqualIgnoringCase "c"

                // d repeated 3 times
                events[2].data.note shouldBeEqualIgnoringCase "d"
                events[3].data.note shouldBeEqualIgnoringCase "d"

                // e repeated 2 times
                events[4].data.note shouldBeEqualIgnoringCase "e"
                events[5].data.note shouldBeEqualIgnoringCase "e"
                events[6].data.note shouldBeEqualIgnoringCase "e"

                // f repeated 3 times
                events[7].data.note shouldBeEqualIgnoringCase "f"
                events[8].data.note shouldBeEqualIgnoringCase "f"
                events[9].data.note shouldBeEqualIgnoringCase "f"
            }
        }
    }

    "ply() with control pattern in compiled code" {
        val p = StrudelPattern.compile("""note("c d").ply("2 3")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // c repeated 2 times, d repeated 3 times = 5 total
        events.size shouldBe 5

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[2].data.note shouldBeEqualIgnoringCase "d"
        events[3].data.note shouldBeEqualIgnoringCase "d"
        events[4].data.note shouldBeEqualIgnoringCase "d"
    }
})
