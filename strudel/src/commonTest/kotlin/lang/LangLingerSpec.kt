package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangLingerSpec : StringSpec({

    "s(\"bd sd ht lt\").linger(0.5) repeats first half" {
        val subject = s("bd sd ht lt").linger(0.5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "Cycle $cycle, Event ${index + 1}: sound: ${event.data.sound} | " +
                                    "part: [${event.part.begin}, ${event.part.end}] | " +
                                    "whole: [${event.whole.begin}, ${event.whole.end}]"
                        )
                    }

                    // linger(0.5) takes first 50% (bd sd) and repeats it to fill cycle
                    // The first 50% has 2 events, so we get those 2 events repeated
                    events.size shouldBe 4

                    // Each event should be twice as long (slowed by 0.5)
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.sound shouldBeEqualIgnoringCase "bd"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.sound shouldBeEqualIgnoringCase "sd"
                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd ht lt\").linger(-0.5) repeats last half" {
        val subject = s("bd sd ht lt").linger(-0.5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    // linger(-0.5) takes last 50% (ht lt) and repeats it to fill cycle
                    events.size shouldBe 4

                    // Each event should be twice as long (slowed by 0.5)
                    events[0].data.sound shouldBeEqualIgnoringCase "ht"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.sound shouldBeEqualIgnoringCase "lt"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.sound shouldBeEqualIgnoringCase "ht"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.sound shouldBeEqualIgnoringCase "lt"
                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "seq(\"0 1 2 3\").linger(0.25) repeats first quarter" {
        val subject = seq("0 1 2 3").linger(0.25)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    // linger(0.25) takes first 25% (just "0") and repeats it to fill cycle
                    events.size shouldBe 4

                    // Event should fill the whole cycle (slowed by 0.25)
                    events[0].data.value?.asInt shouldBe 0
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 0
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 0
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.value?.asInt shouldBe 0
                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "note(\"c d e f\").linger(0.75) repeats first 75%" {
        val subject = note("c d e f").linger(0.75)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "Cycle $cycle, Event ${index + 1}: note: ${event.data.note} | " +
                                    "part: [${event.part.begin}, ${event.part.end}] | " +
                                    "whole: [${event.whole.begin}, ${event.whole.end}]"
                        )
                    }

                    // linger(0.75) takes first 75% (c d e) and repeats it to fill cycle
                    events.size shouldBe 4

                    // Each event should be slowed by 0.75
                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.note shouldBeEqualIgnoringCase "d"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.note shouldBeEqualIgnoringCase "e"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.note shouldBeEqualIgnoringCase "c"
                    events[3].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd\").linger(0) returns silence" {
        val subject = s("bd sd").linger(0.0)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    // linger(0) should return silence
                    events.size shouldBe 0
                }
            }
        }
    }

    "s(\"bd sd ht lt\").linger(\"<1 0.5 0.25>\") with control pattern" {
        val subject = s("bd sd ht lt").linger("<1 0.5 0.25>")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }

                    when (cycle % 3) {
                        0 -> {
                            // linger(1): full pattern, no change
                            events.size shouldBe 4
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                            events[2].data.sound shouldBeEqualIgnoringCase "ht"
                            events[3].data.sound shouldBeEqualIgnoringCase "lt"
                        }

                        1 -> {
                            // linger(0.5): first half repeated
                            events.size shouldBe 2
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                            events[1].data.sound shouldBeEqualIgnoringCase "sd"
                        }

                        2 -> {
                            // linger(0.25): first quarter repeated
                            events.size shouldBe 1
                            events[0].data.sound shouldBeEqualIgnoringCase "bd"
                        }
                    }
                }
            }
        }
    }
})
