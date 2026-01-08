package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangStackSpec : StringSpec({

    "stack() plays multiple patterns simultaneously" {
        // Given multiple patterns
        val p = stack(sound("bd"), sound("hh"), sound("sn"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then all patterns play at the same time, spanning the full cycle
        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")

        // All events should start and end at the same time (full cycle)
        events.forEach { event ->
            event.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            event.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "stack() with empty arguments returns silence" {
        // Given an empty stack
        val p = stack()

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get no events
        events.size shouldBe 0
    }

    "stack() with single element works correctly" {
        // Given a stack with one element
        val p = stack(sound("bd"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get one event spanning the full cycle
        events.size shouldBe 1
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "stack() layers patterns with different event counts" {
        // Given patterns with different numbers of events
        val p = stack(sound("bd"), sound("hh cp"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Then we get all events layered
        events.size shouldBe 3

        // bd spans the full cycle
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // hh and cp each take half the cycle
        events[1].data.sound shouldBe "hh"
        events[1].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.sound shouldBe "cp"
        events[2].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "stack() repeats across multiple cycles" {
        // Given stacked patterns
        val p = stack(sound("bd"), sound("hh"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0)

        // Then each pattern repeats in each cycle
        events.size shouldBe 4

        // Count occurrences
        events.count { it.data.sound == "bd" } shouldBe 2
        events.count { it.data.sound == "hh" } shouldBe 2
    }

    "stack() works within compiled code" {
        val p = StrudelPattern.compile("""stack(sound("bd"), sound("hh"), sound("sn"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")
        events.forEach { event ->
            event.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            event.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
