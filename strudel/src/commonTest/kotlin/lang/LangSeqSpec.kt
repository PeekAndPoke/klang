package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSeqSpec : StringSpec({

    "seq() with single argument creates a sequence from mini-notation" {
        // seq("a b") -> sequence of a then b
        val p = seq("a b")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "b"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "seq() with multiple arguments sequences them" {
        // seq("a", "b") -> sequence of a then b
        val p = seq("a", "b")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "b"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "seq() works as method on StrudelPattern" {
        // note("a").seq("b") -> sequence of a then b
        val p = note("a").seq("b")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Note: note("a") sets note="a". "b" via seq default modifier sets value="b" (and note="b").
        // But note("a") puts it in 'note' field.
        // Let's check what they produce.

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b" // "b" string arg goes to value by defaultModifier in seq
    }

    "seq() works as extension on String" {
        // "a".seq("b")
        val p = "a".seq("b")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        // "a" parsed via seq default modifier -> value="a"
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b"
    }

    "seq() works in compiled code" {
        val p = StrudelPattern.compile("""seq("a", "b")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b"
    }

    "seq() works as method in compiled code" {
        // note("a").seq("b")
        val p = StrudelPattern.compile("""note("a").seq("b")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b"
    }

    "seq() works as string extension in compiled code" {
        // "a".seq("b")
        val p = StrudelPattern.compile(""""a".seq("b")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "b"
    }

    "seq(\"0 1 2 3\") produces same pattern over 12 cycles" {
        val p = seq("0 1 2 3")

        println("\n=== seq(\"0 1 2 3\") consistency test ===")
        val cycle0Events = p.queryArc(0.0, 1.0)
        val cycle0Values = cycle0Events.map { it.data.value?.asInt }
        println("Cycle 0: $cycle0Values")

        for (cycle in 1..11) {
            val events = p.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            println("Cycle $cycle: $values")

            // Should be identical to cycle 0
            values shouldBe cycle0Values
        }
    }

    "seq(0, 1) produces same pattern over 12 cycles" {
        val p = seq(0, 1)

        assertSoftly {
            for (cycle in 1..11) {
                val cycleDbl = cycle.toDouble()
                val events = p.queryArc(cycleDbl, cycleDbl + 1)
                val values = events.map { it.data.value?.asInt }
                println("Cycle $cycle: $values")

                events.shouldHaveSize(2)

                events[0].data.value?.asInt shouldBe 0
                events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                events[0].end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                events[1].data.value?.asInt shouldBe 1
                events[1].begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                events[1].end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
            }
        }
    }
    "seq(0, \"1 2\") produces same pattern over 12 cycles" {
        val p = seq(0, "1 2")

        assertSoftly {
            for (cycle in 1..11) {
                val cycleDbl = cycle.toDouble()
                val events = p.queryArc(cycleDbl, cycleDbl + 1)
                val values = events.map { it.data.value?.asInt }
                println("Cycle $cycle: $values")

                events.shouldHaveSize(3)

                events[0].data.value?.asInt shouldBe 0
                events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                events[0].end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                events[1].data.value?.asInt shouldBe 1
                events[1].begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                events[1].end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                events[2].data.value?.asInt shouldBe 2
                events[2].begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                events[2].end.toDouble() shouldBe ((cycleDbl + 1.00) plusOrMinus EPSILON)
            }
        }
    }
})
