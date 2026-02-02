package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

/**
 * Tests for pick() with list of patterns (mini-notation strings).
 *
 * This test file focuses on the specific case where pick() is called with a list of
 * mini-notation pattern strings, ensuring proper pattern selection and timing across
 * multiple cycles.
 */
class LangPickListPatternSpec : StringSpec({

    """pick(["bd hh", "sn cp"], "0 1") - comprehensive 12-cycle test""" {
        // This tests the exact failing JS compat example
        val subject = pick(listOf("bd hh", "sn cp"), seq("0 1"))

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
                                    "onset=${event.hasOnset()} | " +
                                    "part=[${event.part.begin}, ${event.part.end}) | " +
                                    "whole=${event.whole?.let { "[${it.begin}, ${it.end})" } ?: "null"}"
                        )
                    }

                    // Filter to only events with onset (should be played)
                    val events = allEvents.filter { it.hasOnset() }

                    println("--- Events with onset (${events.size}) ---")
                    events.forEachIndexed { index, event ->
                        println(
                            "$index: value=${event.data.value?.asString} | " +
                                    "part=[${event.part.begin}, ${event.part.end}) | " +
                                    "whole=[${event.whole!!.begin}, ${event.whole!!.end})"
                        )
                    }

                    // Expected behavior:
                    // Selector "0 1" creates 2 events:
                    //   - Event at [0, 0.5) with index=0 → picks "bd hh"
                    //   - Event at [0.5, 1.0) with index=1 → picks "sn cp"
                    //
                    // "bd hh" mini-notation creates: "bd" at [0, 0.5), "hh" at [0.5, 1.0)
                    // "sn cp" mini-notation creates: "sn" at [0, 0.5), "cp" at [0.5, 1.0)
                    //
                    // pick() uses innerJoin, so:
                    //   - Selector event [0, 0.5) queries "bd hh" → gets "bd" at [0, 0.5)
                    //   - Selector event [0.5, 1.0) queries "sn cp" → gets "cp" at [0.5, 1.0)
                    //
                    // Result: 2 events per cycle

                    events.size shouldBe 2

                    // Event 0: "bd" from first pattern
                    events[0].data.value?.asString shouldBe "bd"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[0].hasOnset() shouldBe true

                    // Event 1: "cp" from second pattern
                    events[1].data.value?.asString shouldBe "cp"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                    events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                    events[1].hasOnset() shouldBe true
                }
            }
        }
    }
})
