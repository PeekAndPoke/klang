package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.pattern.SequencePattern

class MiniNotationBangSpec : StringSpec() {

    fun parse(input: String) = parseMiniNotation(input) { text, _ -> note(text) }

    init {
        "Simple repetition 'a!2'" {
            val pattern = parse("a!2")
            // a!2 is equivalent to "a a"
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

            events.size shouldBe 2

            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "a"
                part.begin.toDouble() shouldBe 0.0
                part.end.toDouble() shouldBe 0.5
            }
            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "a"
                part.begin.toDouble() shouldBe 0.5
                part.end.toDouble() shouldBe 1.0
            }
        }

        "Repetition with default count 'a!'" {
            // a! defaults to a!2
            val pattern = parse("a!")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

            events.size shouldBe 2
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "a"
        }

        "Repetition inside sequence 'a b!2 c'" {
            // Equivalent to "a [b b] c"
            // Wait, does ! replicate the step in-place in the sequence structure?
            // "a b!2 c" -> a, then (b b), then c.
            // If b!2 creates a SequencePattern(b, b), then "a b!2 c" is Sequence(a, Sequence(b,b), c).
            // This means 'a' gets 1/3, 'b!2' gets 1/3 (so each b gets 1/6), 'c' gets 1/3.

            val pattern = parse("a b!2 c")

            pattern.shouldBeInstanceOf<SequencePattern>()
            pattern.patterns.size shouldBe 4

            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

            events.size shouldBe 4 // a, b, b, c

            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[0].part.duration.toDouble() shouldBe (1.0 / 4.0)

            // b!2 takes the middle third. so each b takes 1/6
            events[1].data.note shouldBeEqualIgnoringCase "b"
            events[1].part.duration.toDouble() shouldBe (1.0 / 4.0)
            events[2].data.note shouldBeEqualIgnoringCase "b"
            events[2].part.duration.toDouble() shouldBe (1.0 / 4.0)

            events[3].data.note shouldBeEqualIgnoringCase "c"
            events[3].part.duration.toDouble() shouldBe (1.0 / 4.0)
        }

        "Repetition of group '[a b]!2'" {
            // [a b]!2 -> [a b] [a b]
            val pattern = parse("[a b]!2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

            events.size shouldBe 4 // a b a b

            // Should play twice in the cycle
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "b"
            events[2].data.note shouldBeEqualIgnoringCase "a"
            events[3].data.note shouldBeEqualIgnoringCase "b"
        }

        "Rest repetition '~!2'" {
            val pattern = parse("~!2")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 0
        }

        "Mixed modifiers 'a!2*2'" {
            // a!2 -> "a a". then *2 speeds it up.
            // "a a" normally takes 1 cycle. *2 makes it take 0.5 cycles.
            // So we hear "a a a a" in 1 cycle.

            val pattern = parse("a!2*2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

            events.size shouldBe 4
            events.map { it.data.note?.lowercase() } shouldContainOnly listOf("a")
        }

        "Bang in alternation '<0!3>'" {
            // <0!3> is <(0 0 0)>.
            // Alternation with 1 item means it plays that item every cycle.
            // 0!3 is a sequence "0 0 0".
            // So it should play 3 events per cycle.

            val pattern = parse("<a!3>")
            val events = pattern.queryArc(0.0, 3.0).sortedBy { it.part.begin }

            events.size shouldBe 3
            events.map { it.data.note?.lowercase() } shouldContainOnly listOf("a")
        }

        "Bang in complex alternation '<0 2 0!3>'" {
            // Cycle 0: 0
            // Cycle 1: 2
            // Cycle 2: 0 0 0
            val pattern = parse("<a b a!3>")

            val events = pattern.queryArc(0.0, 5.0)
            events.size shouldBe 5
            events.map { it.data.note?.lowercase() } shouldBe listOf("a", "b", "a", "a", "a")
        }

        "Complex Pattern: <0 2 4 6 ~ 4 ~ 2 0!3 ~!5>*8" {
            // New logic: ! expands the sequence.
            // Items:
            // 0, 2, 4, 6, ~, 4, ~, 2 (8 items)
            // 0!3 -> 0, 0, 0 (3 items)
            // ~!5 -> ~, ~, ~, ~, ~ (5 items)
            // Total items = 8 + 3 + 5 = 16 items.

            // Alternation <...> plays one item per cycle.
            // Base duration = 16 cycles.
            // *8 speeds up by 8x.
            // Total duration = 16 / 8 = 2 cycles.
            // Each item takes 1 cycle / 8 = 0.125 cycles.

            val pattern = parse("<a b c d ~ c ~ b a!3 ~!5>*8")

            // Expected Events:
            // a, b, c, d, c, b (6 notes)
            // a, a, a (3 notes)
            // Total 9 notes.

            val events = pattern.queryArc(0.0, 2.0).sortedBy { it.part.begin }
            events.size shouldBe 9

            // Check timing
            // Step 0 (a): 0.0 .. 0.125
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

            // Step 8 (first of a!3): 8 * 0.125 = 1.0
            // It should be a full step now!
            val step8 =
                events.find { it.part.begin.toDouble() >= 0.999 && it.part.begin.toDouble() < 1.001 }!!

            step8.data.note shouldBeEqualIgnoringCase "a"
            step8.part.duration.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

            // Step 9 (second of a!3): 1.125
            val step9 =
                events.find { it.part.begin.toDouble() >= 1.124 && it.part.begin.toDouble() < 1.126 }!!

            step9.data.note shouldBeEqualIgnoringCase "a"

            // Step 10 (third of a!3): 1.25
            val step10 =
                events.find { it.part.begin.toDouble() >= 1.249 && it.part.begin.toDouble() < 1.251 }!!

            step10.data.note shouldBeEqualIgnoringCase "a"

            // Rests follow from 1.375 onwards.
        }
    }
}
