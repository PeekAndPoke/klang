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

class LangEarlyInCycleSpec : StringSpec({

    "earlyInCycle dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.earlyInCycle" to
                    seq(pat).earlyInCycle(ctrl),
            "string.earlyInCycle" to
                    pat.earlyInCycle(ctrl),
            "earlyInCycle" to
                    seq(pat).apply(earlyInCycle(ctrl)),
            "script pattern.earlyInCycle" to
                    StrudelPattern.compile("""seq("$pat").earlyInCycle("$ctrl")"""),
            "script string.earlyInCycle" to
                    StrudelPattern.compile(""""$pat".earlyInCycle("$ctrl")"""),
            "script earlyInCycle" to
                    StrudelPattern.compile("""seq("$pat").apply(earlyInCycle("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "earlyInCycle(0.25) shifts events within the cycle without pulling from next cycle" {
        // pattern: "c d" (c: 0-0.5, d: 0.5-1.0)
        // earlyInCycle(0.25):
        // "c" -> -0.25-0.25 (clipped to 0-0.25)
        // "d" -> 0.25-0.75
        // Crucially, NO event from next cycle (e.g. "c" from +1) should appear at 0.75-1.0
        val p = note("c d").earlyInCycle(0.25)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        assertSoftly {
            events.size shouldBe 1

            // Event 1: "d" shifted back
            events[0].data.note shouldBe "d"
            events[0].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[0].whole.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        }
    }

    "earlyInCycle(0.5) on 'c d' leaves second half empty" {
        // "c" -> -0.5-0.0 (completely out of 0-1 cycle)
        // "d" -> 0.0-0.5
        val p = note("c d").earlyInCycle(0.5)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        assertSoftly {
            events.size shouldBe 1

            events[0].data.note shouldBe "d"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        }
    }
})
