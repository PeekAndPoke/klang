package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBrakSpec : StringSpec({

    "brak() alternates normal and syncopated cycles" {
        val pattern = seq("a", "b").brak()

        // Cycle 0: normal (false condition) - should have both events
        val cycle0 = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        cycle0.size shouldBe 2
        cycle0[0].data.value?.asString shouldBe "a"
        cycle0[1].data.value?.asString shouldBe "b"

        // Cycle 1: syncopated (true condition) - should have modified pattern
        // fastcat(pattern, silence).late(0.25)
        val cycle1 = pattern.queryArc(1.0, 2.0).sortedBy { it.part.begin }
        // First half sped up, then silence, all delayed by 0.25
        cycle1.size shouldBe 2
        // Events should be shifted by 0.25 cycles
        cycle1[0].part.begin.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
        cycle1[1].part.begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
    }

    "brak() cycle 0 plays normally" {
        val pattern = note("c e g").brak()
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "c"
        events[1].data.note shouldBe "e"
        events[2].data.note shouldBe "g"

        // Timings should be normal (0.0, 1/3, 2/3)
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)
        events[2].part.begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
    }

    "brak() cycle 2 plays normally" {
        val pattern = note("c e g").brak()
        val events = pattern.queryArc(2.0, 3.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "c"
        events[1].data.note shouldBe "e"
        events[2].data.note shouldBe "g"

        // Timings should be normal (offset by 2 cycles)
        events[0].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (2.0 + 1.0 / 3.0 plusOrMinus EPSILON)
        events[2].part.begin.toDouble() shouldBe (2.0 + 2.0 / 3.0 plusOrMinus EPSILON)
    }

    "brak() works as standalone function" {
        val pattern = brak(note("a b"))
        val cycle0 = pattern.queryArc(0.0, 1.0)

        cycle0.size shouldBe 2
    }

    "brak() works as string extension" {
        val pattern = "a b".brak()
        val cycle0 = pattern.queryArc(0.0, 1.0)

        cycle0.size shouldBe 2
    }

    "brak() works in compiled code" {
        val pattern = StrudelPattern.compile("""seq("a", "b").brak()""")
        val events = pattern?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
    }

    "brak() with fast makes breakbeat pattern" {
        // JavaScript test: sequence('a', 'b').brak()._fast(2)
        // should equal: sequence('a', 'b', fastcat(silence, 'a'), fastcat('b', silence))
        val pattern = seq("a", "b").brak().fast(2)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Should have 4 events total:
        // - Quarter 0: "a" (normal)
        // - Quarter 1: "b" (normal)
        // - Quarter 2: "a" (syncopated, after silence)
        // - Quarter 3: "b" (syncopated, before silence)
        events.size shouldBe 4
    }

    "brak() effect is breakbeat-style syncopation" {
        val pattern = sound("bd sd").brak()

        // Cycle 0: straight beat
        val cycle0 = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        cycle0.size shouldBe 2
        cycle0[0].data.sound shouldBe "bd"
        cycle0[1].data.sound shouldBe "sd"

        // Cycle 1: syncopated
        val cycle1 = pattern.queryArc(1.0, 2.0).sortedBy { it.part.begin }
        cycle1.size shouldBe 2
        // Both events shifted by 0.25 cycles
        cycle1[0].data.sound shouldBe "bd"
        cycle1[1].data.sound shouldBe "sd"
        cycle1[0].part.begin.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
    }

    "brak() repeats pattern every 2 cycles" {
        val pattern = note("c").brak()

        // Cycles 0, 2, 4 should be similar (normal)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        val cycle2 = pattern.queryArc(2.0, 3.0)
        val cycle4 = pattern.queryArc(4.0, 5.0)

        cycle0.size shouldBe 1
        cycle2.size shouldBe 1
        cycle4.size shouldBe 1

        // Cycles 1, 3, 5 should be similar (syncopated)
        val cycle1 = pattern.queryArc(1.0, 2.0)
        val cycle3 = pattern.queryArc(3.0, 4.0)
        val cycle5 = pattern.queryArc(5.0, 6.0)

        cycle1.size shouldBe 1
        cycle3.size shouldBe 1
        cycle5.size shouldBe 1
    }
})
