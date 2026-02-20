package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangGapSpec : StringSpec({

    "gap() with no arguments creates silence lasting 1 cycle" {
        val p = gap()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "gap(1) creates silence lasting 1 cycle" {
        val p = gap(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "gap(3) creates silence lasting 3 cycles" {
        val p = gap(3)
        // Should be empty in first 3 cycles
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 0
    }

    "gap(2) behaves like silence.slow(2)" {
        val p1 = gap(2)
        val p2 = silence.slow(2)

        p1.queryArc(0.0, 2.0).size shouldBe p2.queryArc(0.0, 2.0).size
        p1.queryArc(0.0, 2.0).size shouldBe 0
    }

    "gap() works as method on StrudelPattern" {
        // note("c d").gap(2) -> silence lasting 2 cycles
        val p = note("c d").gap(2)
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 0
    }

    "gap() works as extension on String" {
        // "a b c".gap(3)
        val p = "a b c".gap(3)
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 0
    }

    "gap() works in compiled code" {
        val p = StrudelPattern.compile("""gap(2)""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() works as method in compiled code" {
        // note("c d").gap(2)
        val p = StrudelPattern.compile("""note("c d").gap(2)""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() works as string extension in compiled code" {
        // "a b c".gap(3)
        val p = StrudelPattern.compile(""""a b c".gap(3)""")
        val events = p?.queryArc(0.0, 3.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() with fractional steps" {
        // gap(0.5) should create silence lasting half a cycle
        val p = gap(0.5)
        val events = p.queryArc(0.0, 0.5)

        events.size shouldBe 0
    }

    "gap() with discrete pattern control" {
        // gap("1 2 1 2") - pattern-controlled silence duration
        val p = gap("1 2 1 2")
        val events = p.queryArc(0.0, 1.0)

        // Gap always produces silence regardless of control pattern
        events.size shouldBe 0
    }

    "gap() with continuous pattern control (irand)" {
        // gap(irand(4).segment(4)) - continuous pattern control
        val p = gap(irand(4).segment(4))
        val events = p.queryArc(0.0, 1.0)

        // Gap always produces silence regardless of control pattern
        events.size shouldBe 0
    }

    "gap() with control pattern works in compiled code" {
        val p = StrudelPattern.compile("""gap("1 2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }

    "gap() as method with control pattern" {
        // note("c d").gap("2 3") - method with pattern control
        val p = note("c d").gap("2 3")
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 0
    }

    "gap() with steady pattern produces same result as static value" {
        val p1 = gap(2)
        val p2 = gap(steady(2))

        p1.queryArc(0.0, 2.0).size shouldBe p2.queryArc(0.0, 2.0).size
        p1.queryArc(0.0, 2.0).size shouldBe 0
    }

    // -- Proportional space --------------------------------------------------------------------------------------------

    "gap(2) takes twice the proportional space of adjacent 1-step elements in seq" {
        // Total weight = 1 + 2 + 1 = 4  →  bd=[0,1/4]  gap=[1/4,3/4] (silent)  hh=[3/4,1]
        val p = seq("bd", gap(2), "hh")
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        events.size shouldBe 2

        events[0].data.value?.asString shouldBe "bd"
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "hh"
        events[1].whole.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[1].whole.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "gap(1) gives equal space to all elements in seq" {
        // Total weight = 1 + 1 + 1 = 3  →  bd=[0,1/3]  gap=[1/3,2/3] (silent)  hh=[2/3,1]
        val p = seq("bd", gap(1), "hh")
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        events.size shouldBe 2

        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)

        events[1].whole.begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
        events[1].whole.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    // -- Control pattern support ---------------------------------------------------------------------------------------

    "gap() with alternating control pattern produces silence in every cycle" {
        val p = gap("<1 2>")

        repeat(4) { cycle ->
            p.queryArc(cycle.toDouble(), cycle + 1.0).size shouldBe 0
        }
    }
})
