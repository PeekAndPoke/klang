package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCatSpec : StringSpec({

    "cat() plays each pattern for exactly one cycle" {
        // Given two patterns, each normally 1 cycle long
        val p1 = note("a")
        val p2 = note("b")

        // When concatenated
        val p = cat(p1, p2)

        // Then querying two cycles should reveal both in full
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 2

        // First cycle: a
        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Second cycle: b
        events[1].data.note shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "cat() handles patterns with multiple events" {
        // Given a pattern with 2 steps and another with 1 step
        val p1 = note("a b")
        val p2 = note("c")

        val p = cat(p1, p2)

        // When querying the full span (2 cycles)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 3

        // Cycle 1: a b (still takes 1 full cycle, but each note is 0.5)
        events[0].data.note shouldBe "a"
        events[0].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.note shouldBe "b"
        events[1].dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Cycle 2: c
        events[2].data.note shouldBe "c"
        events[2].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].dur.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "cat() works as a method on StrudelPattern" {
        // Given a pattern
        val p1 = note("a")

        // When chaining cat()
        val p = p1.cat(note("b"), note("c"))

        // Then it should sequence them (total 3 cycles)
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        events[1].data.note shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.note shouldBe "c"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "cat() works as an extension on String" {
        // When using string extension "a".cat(...)
        val p = "a".cat("b", "c")

        // Then it should sequence them (total 3 cycles)
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.value?.asString shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.value?.asString shouldBe "c"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "cat() works within compiled code" {
        val p = StrudelPattern.compile("""cat(note("a"), note("b"))""")

        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "cat() works as method in compiled code" {
        val p = StrudelPattern.compile("""note("a").cat(note("b"))""")

        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "cat() works as string extension in compiled code" {
        val p = StrudelPattern.compile(""""a".cat("b")""")

        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }
})
