package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangArrangeSpec : StringSpec({

    "arrange() with simple patterns defaults to 1 cycle each" {
        // Given two patterns without duration specification
        val p = arrange(sound("bd"), sound("hh"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Then each pattern takes 1 cycle
        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "hh"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "arrange() with duration specification [2, pattern]" {
        // Given a pattern that should play for 2 cycles
        val p = arrange(listOf(2, sound("bd")), sound("hh"))

        // When querying three cycles
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        // Then bd plays for 2 cycles, hh for 1
        events.size shouldBe 3

        // First two cycles: bd
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "bd"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Third cycle: hh
        events[2].data.sound shouldBe "hh"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "arrange() with mixed durations" {
        // Given patterns with different durations
        val p = arrange(
            listOf(2, sound("bd")),
            sound("hh"),
            listOf(3, sound("sn"))
        )

        // When querying the full span (6 cycles)
        val events = p.queryArc(0.0, 6.0).sortedBy { it.begin }

        // Then we get 2 bd + 1 hh + 3 sn = 6 events
        events.size shouldBe 6

        // Count occurrences
        events.count { it.data.sound == "bd" } shouldBe 2
        events.count { it.data.sound == "hh" } shouldBe 1
        events.count { it.data.sound == "sn" } shouldBe 3

        // Check order: bd, bd, hh, sn, sn, sn
        events.map { it.data.sound } shouldBe listOf("bd", "bd", "hh", "sn", "sn", "sn")
    }

    "arrange() with single element list [pattern]" {
        // Given a pattern in a single-element list (should default to 1 cycle)
        val p = arrange(listOf(sound("bd")))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then it plays for 1 cycle
        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "arrange() with empty arguments returns silence" {
        // Given an empty arrangement
        val p = arrange()

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get no events
        events.size shouldBe 0
    }

    "arrange() repeats the full arrangement" {
        // Given a short arrangement
        val p = arrange(sound("bd"), sound("hh"))

        // When querying beyond the arrangement length (4 cycles)
        val events = p.queryArc(0.0, 4.0).sortedBy { it.begin }

        // Then the arrangement repeats
        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "bd", "hh")
    }

    "arrange() works within compiled code" {
        val p = StrudelPattern.compile("""arrange([2, sound("bd")], sound("hh"))""")

        val events = p?.queryArc(0.0, 3.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 3
        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "bd"
        events[2].data.sound shouldBe "hh"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }
})
