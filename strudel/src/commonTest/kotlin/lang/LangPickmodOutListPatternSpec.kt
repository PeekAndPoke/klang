package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

/**
 * Tests for pickmodOut() with list of patterns (mini-notation strings).
 *
 * This test file focuses on the specific case where pickmodOut() is called with a list of
 * mini-notation pattern strings, ensuring proper pattern selection with modulo wrapping
 * and timing across multiple cycles.
 *
 * Note: pickmodOut() currently behaves like pickmod() (innerJoin with clipping and modulo wrapping).
 */
class LangPickmodOutListPatternSpec : StringSpec({

    """pickmodOut(["bd hh", "sd oh"], "0 1 2") - comprehensive 12-cycle test""" {
        // This tests the exact failing JS compat example
        val subject = pickmodOut(listOf("bd hh", "sd oh"), seq("0 1 2"))

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val allEvents = subject.queryArc(cycleDbl, cycleDbl + 1)

                    // Debug: print all events including those without onset
                    println("=== Cycle $cycle - All events (${allEvents.size}) ===")
                    allEvents.forEachIndexed { index, event ->
                        println(
                            "$index: value=${event.data.value?.asString} | " +
                                    "onset=${event.isOnset} | " +
                                    "part=[${event.part.begin}, ${event.part.end}) | " +
                                    "whole=${event.whole?.let { "[${it.begin}, ${it.end})" } ?: "null"}"
                        )
                    }

                    // Filter to only events with onset (should be played)
                    val events = allEvents.filter { it.isOnset }

                    println("--- Events with onset (${events.size}) ---")
                    events.forEachIndexed { index, event ->
                        println(
                            "$index: value=${event.data.value?.asString} | " +
                                    "part=[${event.part.begin}, ${event.part.end}) | " +
                                    "whole=[${event.whole!!.begin}, ${event.whole!!.end})"
                        )
                    }

                    // Expected behavior:
                    // Selector "0 1 2" creates 3 events per cycle:
                    //   - Event at [0, 1/3) with index=0 % 2 = 0 → picks "bd hh"
                    //   - Event at [1/3, 2/3) with index=1 % 2 = 1 → picks "sd oh"
                    //   - Event at [2/3, 1.0) with index=2 % 2 = 0 → picks "bd hh"
                    //
                    // "bd hh" mini-notation creates: "bd" at [0, 0.5), "hh" at [0.5, 1.0)
                    // "sd oh" mini-notation creates: "sd" at [0, 0.5), "oh" at [0.5, 1.0)
                    //
                    // pickmodOut() uses outerJoin: sets whole to selector's timespan!
                    // This means all events get onset at their selector's beginning:
                    //   - Selector [0, 1/3) queries "bd hh" → "bd" [0, 1/3) with whole=[0, 1/3) (onset!)
                    //   - Selector [1/3, 2/3) queries "sd oh" → "sd" [1/3, 0.5) with whole=[1/3, 2/3) (onset!)
                    //   - Selector [2/3, 1.0) queries "bd hh" → "hh" [2/3, 1.0) with whole=[2/3, 1.0) (onset!)
                    //
                    // Result: 3 onset events per cycle (bd, sd, hh) - matches JavaScript!

                    events.size shouldBe 3

                    // Event 0: "bd" from first pattern (index 0)
                    events[0].data.value?.asString shouldBe "bd"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                    events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                    events[0].isOnset shouldBe true

                    // Event 1: "sd" from second pattern (index 1)
                    events[1].data.value?.asString shouldBe "sd"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                    events[1].whole!!.end.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                    events[1].isOnset shouldBe true

                    // Event 2: "hh" from first pattern (index 2 % 2 = 0, wraps to "bd hh")
                    events[2].data.value?.asString shouldBe "hh"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                    events[2].whole!!.begin.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                    events[2].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                    events[2].isOnset shouldBe true
                }
            }
        }
    }
})
