package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSlowSpec : StringSpec({

    "slow() stretches a pattern by the given factor" {
        // Given a pattern with two sounds in one cycle
        val p = sound("bd hh").slow(2)

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        // Then the two sounds are stretched across 2 cycles
        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "hh"
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "slow() works as a standalone function slow(factor, pattern)" {
        // slow(2, sound("bd hh"))
        val p = slow(2, sound("bd hh"))

        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "slow() works as extension on String" {
        // "bd hh".slow(2)
        val p = "bd hh".slow(2)

        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        // "bd hh" parses to note("bd hh") by default
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "bd"
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "slow() with factor 1 leaves pattern unchanged" {
        // Given a pattern slowed by 1
        val p = sound("bd hh").slow(1)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then it plays normally
        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
        events[0].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "slow() with large factor" {
        // Given a pattern slowed by 4
        val p = sound("bd hh sn cp").slow(4)

        // When querying four cycles
        val events = p.queryArc(0.0, 4.0).sortedBy { it.part.begin }

        // Then each sound takes 1 full cycle
        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn", "cp")

        events.forEach { event ->
            event.part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "slow() can be chained multiple times" {
        // Given a pattern slowed twice
        val p = sound("bd hh").slow(2).slow(2)

        // When querying four cycles
        val allEvents = p.queryArc(0.0, 4.0).sortedBy { it.part.begin }

        val events = allEvents.filter { it.isOnset }

        // Then the pattern is slowed by 4 total (2 * 2)
        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].whole.duration.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].whole.duration.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "slow() with fractional factor" {
        // Given a pattern slowed by 0.5 (which actually speeds it up)
        val p = sound("bd").slow(0.5)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get two events (pattern plays twice as fast)
        events.size shouldBe 2
        events.all { it.data.sound == "bd" } shouldBe true
        events[0].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "slow() works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").slow(2)""")

        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "slow() as function works in compiled code" {
        val p = StrudelPattern.compile("""slow(2, sound("bd hh"))""")

        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "slow() with discrete pattern control" {
        // sound("bd hh").slow("1 2") - pattern-controlled slow
        val p = sound("bd hh").slow("1 2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // With "1 2" control pattern, first half slowed by 1, second half by 2
        // Control pattern creates 2 events, each applying different slow factor
        events.size shouldBe 2
    }

    "slow() with continuous pattern control (sine)" {
        // sound("bd hh").slow(sine.range(1, 3).segment(2))
        val p = sound("bd hh").slow(sine.range(1, 3).segment(2))
        val events = p.queryArc(0.0, 1.0)

        // Should have events with varying slow factors
        events.size shouldBe 2
    }

    "slow() with control pattern works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").slow("1 2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Should work with pattern control
        events.isNotEmpty() shouldBe true
    }

    "slow() with steady pattern produces same result as static value" {
        val p1 = sound("bd hh").slow(2)
        val p2 = sound("bd hh").slow(steady(2))

        val events1 = p1.queryArc(0.0, 2.0).sortedBy { it.part.begin }
        val events2 = p2.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.part.begin shouldBe e2.part.begin
            e1.part.end shouldBe e2.part.end
            e1.data.sound shouldBe e2.data.sound
        }
    }
})
