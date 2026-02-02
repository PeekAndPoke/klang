package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangDropSpec : StringSpec({

    "note(\"c d e f\").drop(1) - drops first step, stretches remaining" {
        // Pattern: note("c d e f") creates 4 events: c[0, 0.25), d[0.25, 0.5), e[0.5, 0.75), f[0.75, 1.0)
        // drop(1) removes first step (c), stretches remaining 3 to fill cycle
        // Expected: d[0, 0.33), e[0.33, 0.66), f[0.66, 1.0)

        val subject = note("c d e f").drop(1)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    // Expect 3 events per cycle (d, e, f - c is dropped)
                    events.size shouldBe 3

                    // Event 0: "d" note (first after drop)
                    withClue("Event 0 (d)") {
                        events[0].data.note shouldBeEqualIgnoringCase "d"

                        // Part: stretched to [0, 1/3)
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)

                        // Whole: should match part for new events
                        events[0].whole.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].whole.end.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)

                        // Onset: first event in cycle
                        events[0].isOnset shouldBe true
                    }

                    // Event 1: "e" note
                    withClue("Event 1 (e)") {
                        events[1].data.note shouldBeEqualIgnoringCase "e"

                        // Part: stretched to [1/3, 2/3)
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)

                        // Whole: should match part
                        events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 1.0 / 3.0) plusOrMinus EPSILON)
                        events[1].whole.end.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)

                        // Onset: starts at its part.begin
                        events[1].isOnset shouldBe true
                    }

                    // Event 2: "f" note
                    withClue("Event 2 (f)") {
                        events[2].data.note shouldBeEqualIgnoringCase "f"

                        // Part: stretched to [2/3, 1.0)
                        events[2].part.begin.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                        events[2].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Whole: should match part
                        events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 2.0 / 3.0) plusOrMinus EPSILON)
                        events[2].whole.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Onset: starts at its part.begin
                        events[2].isOnset shouldBe true
                    }
                }
            }
        }
    }

    "drop() skips first n steps and stretches remaining" {
        val p = note("c d e f").drop(2)

        // Should drop first 2 steps (c, d), keep last 2 (e, f) stretched to fill cycle
        val events = p.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events.map { it.data.note } shouldBe listOf("e", "f")
        // Events stretched: e occupies 0.0-0.5, f occupies 0.5-1.0
        events[0].part.begin.toDouble() shouldBe 0.0
        events[0].part.end.toDouble() shouldBe 0.5
        events[1].part.begin.toDouble() shouldBe 0.5
        events[1].part.end.toDouble() shouldBe 1.0
    }
})
