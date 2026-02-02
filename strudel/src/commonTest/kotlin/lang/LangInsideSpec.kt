package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangInsideSpec : StringSpec({

    "p.inside(1, x => x)" {

        val p = seq("0 1")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map { it.data.value?.asInt }
                    println("Cycle $cycle: $values")

                    events.shouldHaveSize(2)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "p.inside(2, x => x.add(\"0 10\"))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.add("0 10") }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map { it.data.value?.asInt }

                    // Expected values: 0, 1, 12, 13
                    println("Cycle $cycle: $values")

                    events.shouldHaveSize(4)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 12
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.value?.asInt shouldBe 13
                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "p.inside(2, x => x.late(0.1))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.late(0.1) }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.value?.asString} | " +
                                    "onset: ${event.hasOnset()} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    // Expect 5 events: tail from previous cycle + 4 new events
                    events.shouldHaveSize(5)

                    // Event 0: value=3 tail from previous cycle (no onset)
                    withClue("Event 0 (tail of 3)") {
                        events[0].data.value?.asInt shouldBe 3
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl - 0.15) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[0].whole.begin.toDouble() shouldBe ((cycleDbl - 0.15) plusOrMinus EPSILON)
                        events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[0].hasOnset() shouldBe true
                    }

                    // Event 1: value=0 (has onset)
                    withClue("Event 1 (value 0)") {
                        events[1].data.value?.asInt shouldBe 0
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.35) plusOrMinus EPSILON)
                        events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.35) plusOrMinus EPSILON)
                        events[1].hasOnset() shouldBe true
                    }

                    // Event 2: value=1 (has onset)
                    withClue("Event 2 (value 1)") {
                        events[2].data.value?.asInt shouldBe 1
                        events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.35) plusOrMinus EPSILON)
                        events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.35) plusOrMinus EPSILON)
                        events[2].whole.end.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[2].hasOnset() shouldBe true
                    }

                    // Event 3: value=2 (has onset)
                    withClue("Event 3 (value 2)") {
                        events[3].data.value?.asInt shouldBe 2
                        events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[3].part.end.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[3].whole.begin.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[3].whole.end.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[3].hasOnset() shouldBe true
                    }

                    // Event 4: value=3 (has onset, but clipped)
                    withClue("Event 4 (value 3)") {
                        events[4].data.value?.asInt shouldBe 3
                        events[4].part.begin.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[4].part.end.toDouble() shouldBe ((cycleDbl + 1.1) plusOrMinus EPSILON)
                        events[4].whole.begin.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[4].whole.end.toDouble() shouldBe ((cycleDbl + 1.1) plusOrMinus EPSILON)
                        events[4].hasOnset() shouldBe true
                    }
                }
            }
        }
    }

    "p.inside(2, x => x.late(\"0 0.1\"))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.late("0 0.1") }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.value?.asString} | " +
                                    "onset: ${event.hasOnset()} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    // Expect 5 events: late("0 0.1") creates overlapping delayed versions
                    events.shouldHaveSize(5)

                    withClue("Event 0 (value 3, delay from previous cycle)") {
                        events[0].data.value?.asInt shouldBe 3
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl - 0.15) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[0].whole.begin.toDouble() shouldBe ((cycleDbl - 0.15) plusOrMinus EPSILON)
                        events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.1) plusOrMinus EPSILON)
                        events[0].hasOnset() shouldBe true
                    }

                    withClue("Event 1 (value 0, no delay)") {
                        events[1].data.value?.asInt shouldBe 0
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                        events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                        events[1].hasOnset() shouldBe true
                    }

                    withClue("Event 2 (value 1, no delay") {
                        events[2].data.value?.asInt shouldBe 1
                        events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                        events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                        events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                        events[2].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                        events[2].hasOnset() shouldBe true
                    }

                    withClue("Event 3 (value 2, delay by 0.1)") {
                        events[3].data.value?.asInt shouldBe 2
                        events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[3].part.end.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[3].whole.begin.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)
                        events[3].whole.end.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[3].hasOnset() shouldBe true
                    }

                    withClue("Event 4 (value 3, delayed by 0.1)") {
                        events[4].data.value?.asInt shouldBe 3
                        events[4].part.begin.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[4].part.end.toDouble() shouldBe ((cycleDbl + 1.1) plusOrMinus EPSILON)
                        events[4].whole.begin.toDouble() shouldBe ((cycleDbl + 0.85) plusOrMinus EPSILON)
                        events[4].whole.end.toDouble() shouldBe ((cycleDbl + 1.1) plusOrMinus EPSILON)
                        events[4].hasOnset() shouldBe true
                    }
                }
            }
        }
    }
})
