package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPickRestartSpec : StringSpec({

    "pickRestart() picks patterns sequentially per cycle" {
        // Given three patterns
        val p = pickRestart(sound("bd"), sound("hh"), sound("sn"))

        // When querying three cycles
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        // Then each pattern plays in its own cycle
        events.size shouldBe 3
        events[0].data.sound shouldBe "bd"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "hh"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        events[2].data.sound shouldBe "sn"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "pickRestart() with patterns containing multiple events" {
        // Given patterns with multiple steps
        val p = pickRestart(sound("bd hh"), sound("sn cp"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Then first cycle has bd and hh, second cycle has sn and cp
        events.size shouldBe 4

        // Cycle 1: bd hh
        events[0].data.sound shouldBe "bd"
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.sound shouldBe "hh"
        events[1].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Cycle 2: sn cp
        events[2].data.sound shouldBe "sn"
        events[2].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.sound shouldBe "cp"
        events[3].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "pickRestart() repeats after all patterns are played" {
        // Given two patterns
        val p = pickRestart(sound("bd"), sound("hh"))

        // When querying four cycles
        val events = p.queryArc(0.0, 4.0).sortedBy { it.begin }

        // Then the pattern sequence repeats
        events.size shouldBe 4
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "bd", "hh")
    }

    "pickRestart() with single pattern" {
        // Given a single pattern
        val p = pickRestart(sound("bd"))

        // When querying two cycles
        val events = p.queryArc(0.0, 2.0)

        // Then it repeats in each cycle
        events.size shouldBe 2
        events.all { it.data.sound == "bd" } shouldBe true
    }

    "pickRestart() with empty arguments returns silence" {
        // Given an empty pickRestart
        val p = pickRestart()

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then we get no events
        events.size shouldBe 0
    }

    "pickRestart() works within compiled code" {
        val p = StrudelPattern.compile("""pickRestart(sound("bd"), sound("hh"), sound("sn"))""")

        val events = p?.queryArc(0.0, 3.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 3
        events.map { it.data.sound } shouldBe listOf("bd", "hh", "sn")
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }
})
