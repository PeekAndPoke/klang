package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq

class LangLateInCycleSpec : StringSpec({

    "lateInCycle dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.55"

        dslInterfaceTests(
            "pattern.lateInCycle" to
                    seq(pat).lateInCycle(ctrl),
            "string.lateInCycle" to
                    pat.lateInCycle(ctrl),
            "lateInCycle" to
                    seq(pat).apply(lateInCycle(ctrl)),
            "script pattern.lateInCycle" to
                    StrudelPattern.compile("""seq("$pat").lateInCycle("$ctrl")"""),
            "script string.lateInCycle" to
                    StrudelPattern.compile(""""$pat".lateInCycle("$ctrl")"""),
            "script lateInCycle" to
                    StrudelPattern.compile("""seq("$pat").apply(lateInCycle("$ctrl")"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "lateInCycle(0.25) shifts events within the cycle without pulling from previous cycle" {
        // pattern: "c d" (c: 0-0.5, d: 0.5-1.0)
        // lateInCycle(0.25):
        // "c" -> 0.25-0.75
        // "d" -> 0.75-1.25 (clipped to 1.0 -> 0.75-1.0)
        // Crucially, NO event from previous cycle (e.g. "d" from -1) should appear at 0-0.25
        val p = note("c d").lateInCycle(0.25)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        assertSoftly {
            events.size shouldBe 2

            // Event 1: "c" shifted
            events[0].data.note shouldBe "c"
            events[0].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[0].whole.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            // Event 2: "d" shifted and clipped
            events[1].data.note shouldBe "d"
            events[1].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            events[1].whole.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[1].whole.end.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
        }
    }

    "lateInCycle(0.5) on 'c d' leaves first half empty" {
        // "c" -> 0.5-1.0
        // "d" -> 1.0-1.5 (completely out of 0-1 cycle)
        val p = note("c d").lateInCycle(0.5)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        assertSoftly {
            events.size shouldBe 1

            events[0].data.note shouldBe "c"
            events[0].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            events[0].whole.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "lateInCycle works with pattern argument" {
        // "c d" shifted by "<0 0.25>"
        // 0-0.5 (offset 0): "c" -> stays 0-0.5
        // 0.5-1.0 (offset 0.25): "d" -> shifts to 0.75-1.25, clipped to 0.75-1.0
        val p = note("c d").lateInCycle("0 0.25")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events.size shouldBe 2

            // "c" not shifted
            events[0].data.note shouldBe "c"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            // "d" shifted by 0.25
            events[1].data.note shouldBe "d"
            events[1].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
