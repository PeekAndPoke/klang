package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangStructSpec : StringSpec({

    "note(\"c\").struct(\"x*2\") - struct creates multiple onset events" {
        // Pattern: note("c") creates one long note [0, 1)
        // struct("x*2") creates 2 pulses: [0, 0.5) and [0.5, 1.0)
        // Expected: 2 separate events, both with onset

        val subject = note("c").struct("x*2")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    // Expect 2 events per cycle
                    events.size shouldBe 2

                    // Event 0: First "c" note (first half of cycle)
                    withClue("Event 0 (c - first half)") {
                        events[0].data.note shouldBeEqualIgnoringCase "c"

                        // Part: first pulse [0, 0.5)
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                        // Whole: struct pulse boundaries (each pulse is independent)
                        events[0].whole.shouldNotBeNull()
                        events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                        // Onset: first pulse starts at its beginning
                        events[0].hasOnset() shouldBe true
                    }

                    // Event 1: Second "c" note (second half of cycle)
                    withClue("Event 1 (c - second half)") {
                        events[1].data.note shouldBeEqualIgnoringCase "c"

                        // Part: second pulse [0.5, 1.0)
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Whole: struct pulse boundaries (each pulse is independent)
                        events[1].whole.shouldNotBeNull()
                        events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                        events[1].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Onset: second pulse starts at its beginning
                        events[1].hasOnset() shouldBe true
                    }
                }
            }
        }
    }

    "note(\"c e\").struct(\"x\") - two events with shared whole" {
        // Pattern: note("c e") creates 2 events: c[0, 0.5) and e[0.5, 1.0)
        // struct("x") sets whole to mask boundaries [0, 1.0) for both
        // Expected: 2 events, both with whole=[0, 1.0), only first has onset

        val subject = note("c e").struct("x")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    // Expect 2 events per cycle
                    events.size shouldBe 2

                    // Event 0: "c" note (has onset)
                    withClue("Event 0 (c)") {
                        events[0].data.note shouldBeEqualIgnoringCase "c"

                        // Part: first half
                        events[0].part.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                        // Whole: struct mask boundaries (shared with event 1)
                        events[0].whole.shouldNotBeNull()
                        events[0].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[0].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Onset: part.begin == whole.begin → true
                        events[0].hasOnset() shouldBe true
                    }

                    // Event 1: "e" note (NO onset - continuation of same whole)
                    withClue("Event 1 (e)") {
                        events[1].data.note shouldBeEqualIgnoringCase "e"

                        // Part: second half
                        events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                        events[1].part.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // Whole: same struct mask boundaries as event 0
                        events[1].whole.shouldNotBeNull()
                        events[1].whole!!.begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                        events[1].whole!!.end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)

                        // No onset: part.begin (0.5) != whole.begin (0.0)
                        events[1].hasOnset() shouldBe false
                    }
                }
            }
        }
    }

    "struct() with mini-notation 'x ~ x' structures a pattern" {
        // Given: note "c e g" (3 items in cycle)
        // Struct: "x ~ x" (x at 0.0-0.33, silence, x at 0.66-1.0)
        val p = note("c e g").struct("x ~ x")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // We expect 2 events:
        // 1. 'c' from the first 'x' slot (0.0 to 0.33)
        // 2. 'g' from the second 'x' slot (0.66 to 1.0)
        events.size shouldBe 2

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "g"
        events[1].part.begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "struct() as a standalone function" {
        val p = struct("x x", note("c e"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.note?.lowercase() } shouldBe listOf("c", "e")
    }

    "struct() as extension on String" {
        val p = "c e".struct("x")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.value?.asString shouldBeEqualIgnoringCase "e"
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "struct() works in compiled code" {
        val p = StrudelPattern.compile("""note("c e g").struct("x ~ x")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "g"
    }
})
