package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangPressSpec : StringSpec({

    "s(\"bd sd\").press() syncopates by shifting events halfway" {
        val subject = s("bd sd").press()

        // Debug: Just check what we get in cycle 0
        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: sound: ${event.data.sound} | " +
                                    "onset: ${event.isOnset} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    // Should produce 2 syncopated events
                    events.size shouldBe 2

                    // First event "bd" originally at [0, 0.5]
                    // After press (pressBy 0.5): starts at 0.25, ends at 0.5
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    // Second event "sd" originally at [0.5, 1.0]
                    // After press: starts at 0.75, ends at 1.0
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd ht\").pressBy(0.5) same as press()" {
        val subject = s("bd sd ht").pressBy(0.5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: sound: ${event.data.sound} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 3

                    // Event "bd" at [0, 1/3] -> [1/6, 1/3]
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 6.0) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)

                    // Event "sd" at [1/3, 2/3] -> [1/2, 2/3]
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)

                    // Event "ht" at [2/3, 1] -> [5/6, 1]
                    events[2].data.sound shouldBeEqualIgnoringCase "ht"
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 5.0 / 6.0) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd\").pressBy(0.75) compresses to start at 3/4" {
        val subject = s("bd sd").pressBy(0.75)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: sound: ${event.data.sound} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 2

                    // Event "bd" originally at [0, 0.5]
                    // pressBy(0.75): starts at 0.375 (0 + 0.75*0.5), ends at 0.5
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.375) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    // Event "sd" originally at [0.5, 1.0]
                    // pressBy(0.75): starts at 0.875 (0.5 + 0.75*0.5), ends at 1.0
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.875) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd\").pressBy(0.25) compresses to start at 1/4" {
        val subject = s("bd sd").pressBy(0.25)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: sound: ${event.data.sound} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 2

                    // Event "bd" originally at [0, 0.5]
                    // pressBy(0.25): starts at 0.125 (0 + 0.25*0.5), ends at 0.5
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.125) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    // Event "sd" originally at [0.5, 1.0]
                    // pressBy(0.25): starts at 0.625 (0.5 + 0.25*0.5), ends at 1.0
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.625) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd\").pressBy(0) produces normal timing (no compression)" {
        val subject = s("bd sd").pressBy(0)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: sound: ${event.data.sound} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 2

                    // Event "bd" at [0, 0.5] - no change with pressBy(0)
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    // Event "sd" at [0.5, 1.0] - no change
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "s(\"bd sd ht\").pressBy(\"<0 0.5 0.75>\") with control pattern" {
        val subject = s("bd sd ht").pressBy("<0 0.5 0.75>")

        assertSoftly {
            // Test 12 cycles to ensure pattern cycles correctly
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "Cycle $cycle, Event ${index + 1}: sound: ${event.data.sound} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 3

                    when (cycle % 3) {
                        0 -> {
                            // Cycle 0: pressBy(0) - no compression
                            events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                            events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                            events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                        }

                        1 -> {
                            // Cycle 1: pressBy(0.5) - start halfway
                            events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 6.0) plusOrMinus EPSILON)
                            events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                            events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 5.0 / 6.0) plusOrMinus EPSILON)
                        }

                        2 -> {
                            // Cycle 2: pressBy(0.75) - start at 3/4
                            events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                            events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 7.0 / 12.0) plusOrMinus EPSILON)
                            events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 11.0 / 12.0) plusOrMinus EPSILON)
                        }
                    }
                }
            }
        }
    }

    "note(\"c e g\").press() works with notes" {
        val subject = note("c e g").press()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.note} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 3

                    // All notes shifted halfway into their timespans
                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 6.0) plusOrMinus EPSILON)

                    events[1].data.note shouldBeEqualIgnoringCase "e"
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.note shouldBeEqualIgnoringCase "g"
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 5.0 / 6.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "seq(\"0 1 2\").pressBy(0.5) works with numeric patterns" {
        val subject = seq("0 1 2").pressBy(0.5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val events = allEvents.filter { it.isOnset }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: value: ${event.data.value} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 3

                    // Verify values and timing
                    events[0].data.value?.asInt shouldBe 0
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 6.0) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 2
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 5.0 / 6.0) plusOrMinus EPSILON)
                }
            }
        }
    }
})
