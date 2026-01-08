package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFastSpec : StringSpec({

    "fast() compresses a pattern by the given factor" {
        // Given a pattern with two sounds in one cycle, sped up by 2
        val p = sound("bd hh").fast(2)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Then the pattern plays twice (4 events total)
        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "bd", "hh")

        // Each event takes 1/4 of a cycle
        events.forEach { event ->
            event.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        }
    }

    "fast() with factor 1 leaves pattern unchanged" {
        // Given a pattern with factor 1
        val p = sound("bd hh").fast(1)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then it plays normally
        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "fast() with large factor" {
        // Given a pattern sped up by 4
        val p = sound("bd").fast(4)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then the pattern repeats 4 times
        events.size shouldBe 4
        events.all { it.data.sound == "bd" } shouldBe true
        events.forEach { event ->
            event.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        }
    }

    "fast() can be chained multiple times" {
        // Given a pattern sped up twice
        val p = sound("bd").fast(2).fast(2)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then the pattern is sped up by 4 total (2 * 2)
        events.size shouldBe 4
        events.all { it.data.sound == "bd" } shouldBe true
        events.forEach { event ->
            event.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        }
    }

    "fast() with fractional factor" {
        // Given a pattern sped up by 0.5 (which actually slows it down)
        val p = sound("bd hh").fast(0.5)

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Then the pattern is stretched (plays half as fast)
        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
        events[0].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "fast() combined with slow() cancels out" {
        // Given a pattern that is slowed and then sped up by the same amount
        val p = sound("bd hh sn").slow(2).fast(2)

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then the pattern should play normally (3 events in one cycle)
        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")
        events.forEach { event ->
            event.dur.toDouble() shouldBe ((1.0 / 3.0) plusOrMinus EPSILON)
        }
    }

    "fast() works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").fast(2)""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "bd", "hh")
        events.forEach { event ->
            event.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        }
    }
})
