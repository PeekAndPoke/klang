package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangSwingSpec : StringSpec({

    "note(\"c d e f\").swing(2) produces events" {
        val p = note("c d e f").swing(2)
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            // Should produce events with swing timing
            events.size shouldBe 4

            events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].end.toDouble() shouldBe (0.3333333333 plusOrMinus EPSILON)

            events[1].begin.toDouble() shouldBe (0.3333333333 plusOrMinus EPSILON)
            events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].end.toDouble() shouldBe (0.8333333333 plusOrMinus EPSILON)

            events[3].begin.toDouble() shouldBe (0.8333333333 plusOrMinus EPSILON)
            events[3].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

            events.forEach {
                (it.end - it.begin).toDouble() shouldBe (it.dur.toDouble() plusOrMinus EPSILON)
            }
        }
    }

    "note(\"[c c] d e [f f]\").swing(2) produces events" {
        val p = note("[c c] d e [f f]").swing(2)
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            // Should produce events with swing timing
            events.size shouldBe 6

            events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].end.toDouble() shouldBe (0.16666666666 plusOrMinus EPSILON)

            events[1].begin.toDouble() shouldBe (0.1666666666 plusOrMinus EPSILON)
            events[1].end.toDouble() shouldBe (0.3333333333 plusOrMinus EPSILON)

            events[2].begin.toDouble() shouldBe (0.3333333333 plusOrMinus EPSILON)
            events[2].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[3].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[3].end.toDouble() shouldBe (0.8333333333 plusOrMinus EPSILON)

            events[4].begin.toDouble() shouldBe (0.8333333333 plusOrMinus EPSILON)
            events[4].end.toDouble() shouldBe (0.9166666666 plusOrMinus EPSILON)

            events[5].begin.toDouble() shouldBe (0.9166666666 plusOrMinus EPSILON)
            events[5].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

            events.forEach {
                (it.end - it.begin).toDouble() shouldBe (it.dur.toDouble() plusOrMinus EPSILON)
            }
        }
    }

    "note(\"c d e f\").swingBy(0.5, 2) produces events" {
        val p = note("c d e f").swingBy(0.5, 2)
        val events = p.queryArc(0.0, 1.0)

        events.forEach {
            println("${it.begin} ${it.end}")
        }

        assertSoftly {
            events.size shouldBe 4

            events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].end.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

            events[1].begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
            events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].end.toDouble() shouldBe (0.875 plusOrMinus EPSILON)

            events[3].begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
            events[3].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "note(\"c d e f\").swingBy(\"[0.5 0.0]\", 2) produces events" {
        val p = note("c d e f").swingBy("[0.5 0.0]", 2)
        val events = p.queryArc(0.0, 1.0)

        events.forEach {
            println("${it.begin} ${it.end}")
        }

        assertSoftly {
            events.size shouldBe 4

            events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].end.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

            events[1].begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
            events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            events[3].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[3].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }


    "swingBy(-0.5, 2) produces events" {
        val p = note("c d e f").swingBy(-0.5, 2)
        val events = p.queryArc(0.0, 1.0)

        events.forEach {
            println("${it.begin} ${it.end}")
        }

        assertSoftly {
            events.size shouldBe 4

            events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

            events[1].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
            events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].end.toDouble() shouldBe (0.625 plusOrMinus EPSILON)

            events[3].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
            events[3].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "swing() is equivalent to swingBy(1/3, n)" {
        val withSwing = note("c d e f").swing(2)
        val withSwingBy = note("c d e f").swingBy(1.0 / 3.0, 2)

        val swingEvents = withSwing.queryArc(0.0, 1.0)
        val swingByEvents = withSwingBy.queryArc(0.0, 1.0)

        assertSoftly {
            swingEvents.size shouldBe swingByEvents.size

            swingEvents.zip(swingByEvents).forEach { (se, sbe) ->
                se.begin shouldBe sbe.begin
                se.end shouldBe sbe.end
            }
        }
    }

    "swing() with subdivision=1 produces events" {
        val p = note("c d e f").swing(1)
        val events = p.queryArc(0.0, 1.0)

        // With subdivision=1, pattern should still produce events (may be more due to wrap-around)
        events.size shouldBeGreaterThan 0
    }
})
