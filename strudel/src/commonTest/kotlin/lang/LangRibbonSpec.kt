package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangRibbonSpec : StringSpec({

    "ribbon(offset, cycles) loops a segment of the pattern" {
        // Pattern: 0 1 2 3 (1 cycle)
        // slow(4) -> 0 (cyc 0), 1 (cyc 1), 2 (cyc 2), 3 (cyc 3)
        val p = note("0 1 2 3").slow(4)

        // ribbon(1, 2)
        // offset 1: starts at cycle 1 ("1")
        // length 2: takes 2 cycles ("1", "2")
        // loops this segment
        val r = p.ribbon(1, 2)

        val events = r.queryArc(0.0, 4.0)

        // Cycle 0..2 (loop 1): should be "1", "2"
        // Cycle 2..4 (loop 2): should be "1", "2"

        events.size shouldBe 4

        // Sort by time
        val sorted = events.sortedBy { it.part.begin }

        // Loop 1
        sorted[0].data.note shouldBe "1"
        sorted[0].part.begin shouldBe Rational(0)

        sorted[1].data.note shouldBe "2"
        sorted[1].part.begin shouldBe Rational(1)

        // Loop 2
        sorted[2].data.note shouldBe "1"
        sorted[2].part.begin shouldBe Rational(2)

        sorted[3].data.note shouldBe "2"
        sorted[3].part.begin shouldBe Rational(3)
    }

    "ribbon works with string extension" {
        // "a b".slow(2) -> "a" (0..1), "b" (1..2)
        // ribbon(1, 1) -> segment [1, 2] -> "b"
        // loop it -> "b", "b", "b"...

        val r = "a b".slow(2).ribbon(1, 1)

        val events = r.queryArc(0.0, 3.0)
        // Should be "b" at 0..1, 1..2, 2..3

        events.size shouldBe 3
        events[0].data.value?.asString shouldBe "b"
        events[0].part.begin shouldBe Rational(0)

        events[1].data.value?.asString shouldBe "b"
        events[1].part.begin shouldBe Rational(1)

        events[2].data.value?.asString shouldBe "b"
        events[2].part.begin shouldBe Rational(2)
    }

    "ribbon works with fractional cycles" {
        // note("a b") -> a(0..0.5), b(0.5..1)
        // ribbon(0.5, 0.5) -> segment [0.5, 1.0] -> "b"
        // loop "b" every 0.5 cycles

        val r = note("a b").ribbon(0.5, 0.5)

        val events = r.queryArc(0.0, 1.0)
        events.size shouldBe 2

        // First occurrence 0.0 .. 0.5
        events[0].data.note shouldBe "b"
        events[0].part.begin shouldBe 0.toRational()
        events[0].part.end shouldBe 0.5.toRational()

        // Second occurrence 0.5 .. 1.0
        events[1].data.note shouldBe "b"
        events[1].part.begin shouldBe 0.5.toRational()
        events[1].part.end shouldBe 1.toRational()
    }

    "ribbon with sound pattern s(\"bd sd ht lt\").slow(4).ribbon(2, 1) produces correct events over 12 cycles" {
        // s("bd sd ht lt") -> bd (0..0.25), sd (0.25..0.5), ht (0.5..0.75), lt (0.75..1)
        // slow(4) -> bd (0..1), sd (1..2), ht (2..3), lt (3..4)
        // ribbon(2, 1) -> segment [2, 3] -> "ht" (1 cycle long)
        // loops "ht" infinitely, every 1 cycle

        val subject = s("bd sd ht lt").slow(4).ribbon(2, 1)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "Cycle $cycle, Event ${index + 1}: sound: ${event.data.sound} | " +
                                    "part: [${event.part.begin}, ${event.part.end}] | " +
                                    "whole: [${event.whole.begin}, ${event.whole.end}]"
                        )
                    }

                    // ribbon(2, 1) extracts "ht" and loops it every cycle
                    events.size shouldBe 1

                    val event = events[0]
                    event.data.sound shouldBe "ht"
                    event.part.begin shouldBe cycle.toRational()
                    event.part.end shouldBe (cycle + 1).toRational()
                    event.whole.begin shouldBe cycle.toRational()
                    event.whole.end shouldBe (cycle + 1).toRational()
                }
            }
        }
    }
})
