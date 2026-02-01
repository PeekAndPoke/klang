package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangEuclidSpec : StringSpec({

    "note(\"x\").euclid(3, 5) - comprehensive test with part/whole assertions" {
        // Euclidean rhythm 3,5 distributes 3 hits across 5 steps as evenly as possible
        // Standard pattern: 10100  (hits at steps 0, 2, 4)
        // Time positions: 0/5, 2/5, 4/5 = 0.0, 0.4, 0.8

        val subject = note("x").euclid(3, 5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    // Expect 3 events per cycle (3 hits)
                    events.size shouldBe 3

                    // Event 0: First hit at position 0/5
                    withClue("Event 0 (position 0/5)") {
                        events[0].data.note shouldBe "x"

                        // Part: [0.0, 0.2) - duration is 1/5
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.2) plusOrMinus EPSILON)

                        // Whole: should match part for new events
                        events[0].whole.shouldNotBeNull()
                        events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 0.2) plusOrMinus EPSILON)

                        // Onset: first event has onset
                        events[0].hasOnset() shouldBe true
                    }

                    // Event 1: Second hit at position 2/5
                    withClue("Event 1 (position 2/5)") {
                        events[1].data.note shouldBe "x"

                        // Part: [0.4, 0.6)
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.4) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)

                        // Whole: should match part
                        events[1].whole.shouldNotBeNull()
                        events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.4) plusOrMinus EPSILON)
                        events[1].whole!!.end.toDouble() shouldBe ((cycleDbl + 0.6) plusOrMinus EPSILON)

                        // Onset: has onset
                        events[1].hasOnset() shouldBe true
                    }

                    // Event 2: Third hit at position 4/5
                    withClue("Event 2 (position 4/5)") {
                        events[2].data.note shouldBe "x"

                        // Part: [0.8, 1.0)
                        events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.8) plusOrMinus EPSILON)
                        events[2].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Whole: should match part
                        events[2].whole.shouldNotBeNull()
                        events[2].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.8) plusOrMinus EPSILON)
                        events[2].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Onset: has onset
                        events[2].hasOnset() shouldBe true
                    }
                }
            }
        }
    }

    "euclid(3, 8) produces correct rhythm" {
        // Standard Euclidean rhythm for 3,8 is 10010010
        // Time steps: 0/8, 1/8, ... 7/8
        // Active steps: 0, 3, 6
        // Times: 0.0, 0.375, 0.75

        val p = note("a").euclid(3, 8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.375
        events[2].part.begin.toDouble() shouldBe 0.75
    }

    "euclid work as top-level function" {
        val p = euclid(3, 8, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }

    "euclid works as string extension" {
        val p = "a".euclid(3, 8)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }
})
