package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangSwingSpec : StringSpec({

    "swingBy dsl interface" {
        val pat = "hh hh"
        val swingAmount = 0.0
        val n = 2

        dslInterfaceTests(
            "pattern.swingBy(swing, n)" to s(pat).swingBy(swingAmount, n),
            "script pattern.swingBy(swing, n)" to StrudelPattern.compile("""s("$pat").swingBy($swingAmount, $n)"""),
            "string.swingBy(swing, n)" to pat.swingBy(swingAmount, n),
            "script string.swingBy(swing, n)" to StrudelPattern.compile(""""$pat".swingBy($swingAmount, $n)"""),
            "swingBy(swing, n)" to s(pat).apply(swingBy(swingAmount, n)),
            "script swingBy(swing, n)" to StrudelPattern.compile("""s("$pat").apply(swingBy($swingAmount, $n))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 2
        }
    }

    "swing dsl interface" {
        val pat = "hh hh"
        val n = 2

        dslInterfaceTests(
            "pattern.swing(n)" to s(pat).swing(n),
            "script pattern.swing(n)" to StrudelPattern.compile("""s("$pat").swing($n)"""),
            "string.swing(n)" to pat.swing(n),
            "script string.swing(n)" to StrudelPattern.compile(""""$pat".swing($n)"""),
            "swing(n)" to s(pat).apply(swing(n)),
            "script swing(n)" to StrudelPattern.compile("""s("$pat").apply(swing($n))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 2
        }
    }

    "note(\"c d e f\").swing(2) produces events" {
        val subject = note("c d e f").swing(2)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    // Filter to only events with onset (should be played)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.note} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    // Should produce events with swing timing
                    events.size shouldBe 4

                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.3333333333) plusOrMinus EPSILON)

                    events[1].data.note shouldBeEqualIgnoringCase "d"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.3333333333) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.note shouldBeEqualIgnoringCase "e"
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 0.8333333333) plusOrMinus EPSILON)

                    events[3].data.note shouldBeEqualIgnoringCase "f"
                    events[3].whole.begin.toDouble() shouldBe ((cycleDbl + 0.8333333333) plusOrMinus EPSILON)
                    events[3].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "note(\"c d e f\").swing(4) produces events" {
        val subject = note("c d e f").swing(4)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    // Filter to only events with onset (should be played)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.note} | " +
                                    "onset: ${event.isOnset} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 4

                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.3333333333) plusOrMinus EPSILON)

                    events[1].data.note shouldBeEqualIgnoringCase "d"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.583333333333) plusOrMinus EPSILON)

                    events[2].data.note shouldBeEqualIgnoringCase "e"
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 0.83333333333) plusOrMinus EPSILON)

                    events[3].data.note shouldBeEqualIgnoringCase "f"
                    events[3].whole.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].whole.end.toDouble() shouldBe ((cycleDbl + 1.08333333333) plusOrMinus EPSILON)
                }
            }
        }
    }

    "note(\"c d e f\").swingBy(0.5, 2) produces events" {
        val p = note("c d e f").swingBy(0.5, 2)
        val allEvents = p.queryArc(0.0, 1.0)
        // Filter to only events with onset (should be played)
        val events = allEvents.filter { it.isOnset }

        events.forEach {
            println("${it.part.begin} ${it.part.end}")
        }

        assertSoftly {
            events.size shouldBe 4

            events[0].data.note shouldBeEqualIgnoringCase "c"
            events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

            events[1].data.note shouldBeEqualIgnoringCase "d"
            events[1].whole.begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
            events[1].whole.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].data.note shouldBeEqualIgnoringCase "e"
            events[2].whole.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].whole.end.toDouble() shouldBe (0.875 plusOrMinus EPSILON)

            events[3].data.note shouldBeEqualIgnoringCase "f"
            events[3].whole.begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
            events[3].whole.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "note(\"c d e f\").swingBy(\"[0.5 0.0]\", 2) produces events" {
        val subject = note("c d e f").swingBy("[0.5 0.0]", 2)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    // Filter to only events with onset (should be played)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.note} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 4

                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.375) plusOrMinus EPSILON)

                    events[1].data.note shouldBeEqualIgnoringCase "d"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.375) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.note shouldBeEqualIgnoringCase "e"
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.note shouldBeEqualIgnoringCase "f"
                    events[3].whole.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "swingBy(-0.5, 2) produces events" {
        val p = note("c d e f").swingBy(-0.5, 2)
        val allEvents = p.queryArc(0.0, 1.0)
        // Filter to only events with onset (should be played)
        val events = allEvents.filter { it.isOnset }

        events.forEachIndexed { index, event ->
            println(
                "${index + 1}: note: ${event.data.note} | " +
                        "part: ${event.part.begin} ${event.part.end} | " +
                        "whole: ${event.whole.begin} ${event.whole.end}"
            )
        }

        assertSoftly {
            events.size shouldBe 4

            events[0].data.note shouldBeEqualIgnoringCase "c"
            events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].whole.end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

            events[1].data.note shouldBeEqualIgnoringCase "d"
            events[1].whole.begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
            events[1].whole.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].data.note shouldBeEqualIgnoringCase "e"
            events[2].whole.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].whole.end.toDouble() shouldBe (0.625 plusOrMinus EPSILON)

            events[3].data.note shouldBeEqualIgnoringCase "f"
            events[3].whole.begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
            events[3].whole.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "swing() is equivalent to swingBy(1/3, n)" {
        val withSwing = note("c d e f").swing(2)
        val withSwingBy = note("c d e f").swingBy(1.0 / 3.0, 2)

        val swingEvents = withSwing.queryArc(0.0, 1.0).filter { it.isOnset }
        val swingByEvents = withSwingBy.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            swingEvents.size shouldBe swingByEvents.size

            swingEvents.zip(swingByEvents).forEach { (se, sbe) ->
                se.whole.begin shouldBe sbe.whole.begin
                se.whole.end shouldBe sbe.whole.end
            }
        }
    }

    "swing() with subdivision=1 produces events" {
        val p = note("c d e f").swing(1)
        val events = p.queryArc(0.0, 1.0)

        // With subdivision=1, pattern should still produce events (may be more due to wrap-around)
        events.size shouldBeGreaterThan 0
    }
})
