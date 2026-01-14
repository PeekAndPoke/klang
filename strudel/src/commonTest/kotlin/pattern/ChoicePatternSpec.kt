package io.peekandpoke.klang.strudel.pattern

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
import io.peekandpoke.klang.strudel.lang.seed

class ChoicePatternSpec : StringSpec() {

    fun parse(input: String) = MiniNotationParser(input) { note(it) }.parse()

    init {
        "Choice (|): Deterministic selection when random seed is set" {
            // "a|b" picks one per cycle
            val pattern = parse("a|b").seed(1)

            pattern.shouldBeInstanceOf<ContextModifierPattern>()

            val choice = pattern.source
            choice.shouldBeInstanceOf<ChoicePattern>()
            choice.choices.size shouldBe 2

            // Cycle 0 should always pick the same one
            val run1 = pattern.queryArc(0.0, 1.0).first().data.note
            val run2 = pattern.queryArc(0.0, 1.0).first().data.note
            run1 shouldBe run2

            // Across many cycles, should be roughly 50/50
            var countA = 0
            var countB = 0
            val totalCycles = 1000
            for (i in 0 until totalCycles) {
                val note = pattern.queryArc(i.toDouble(), i + 1.0).first().data.note
                if (note == "a") countA++ else countB++
            }

            assertSoftly {
                withClue("total count") {
                    (countA + countB) shouldBe totalCycles
                }
                withClue("countA") {
                    countA shouldBeInRange 400..600
                }
            }
        }

        "Choice (|): Multiple options 'a|b|c'" {
            val pattern = parse("a|b|c")

            pattern.shouldBeInstanceOf<ChoicePattern>()
            pattern.choices.size shouldBe 3

            // Should be ~33% each
            var countA = 0
            var countB = 0
            var countC = 0
            val totalCycles = 3000 // 3000 to expect 1000 each

            for (i in 0 until totalCycles) {
                val note = pattern.queryArc(i.toDouble(), i + 1.0).first().data.note
                when (note) {
                    "a" -> countA++
                    "b" -> countB++
                    "c" -> countC++
                }
            }

            // Expect ~1000 each.
            assertSoftly {
                withClue("totalCycles") {
                    (countA + countB + countC) shouldBe totalCycles
                }
                withClue("countA") {
                    countA shouldBeInRange 850..1150
                }
                withClue("countB") {
                    countB shouldBeInRange 850..1150
                }
                withClue("countC") {
                    countC shouldBeInRange 850..1150
                }
            }
        }

        "Complex combination: 'a?|b'" {
            // choice between (a?) and (b)
            // Roughly 50% of cycles: b (always plays)
            // Roughly 50% of cycles: a? (play "a" with 50% prob, or silence)

            val pattern = parse("a?|b")

            pattern.shouldBeInstanceOf<ChoicePattern>()
            pattern.choices.size shouldBe 2
            pattern.choices[0].shouldBeInstanceOf<SometimesPattern>()
            pattern.choices[1].shouldBeInstanceOf<AtomicPattern>().let { choice ->
                choice.data.note shouldBe "b"
            }

            val totalCycles = 1000
            var aCount = 0
            var bCount = 0
            var silenceCount = 0

            for (i in 0 until totalCycles) {
                val events = pattern.queryArc(i.toDouble(), i + 1.0)
                if (events.isEmpty()) {
                    silenceCount++
                } else if (events[0].data.note == "b") {
                    bCount++
                } else if (events[0].data.note == "a") {
                    aCount++
                }
            }

            assertSoftly {
                withClue("totalCycles") {
                    (aCount + bCount + silenceCount) shouldBe totalCycles
                }

                // b should be ~500 (50% of 1000)
                withClue("bCount") { bCount shouldBeInRange 400..600 }

                // remaining 500 cycles are "a?".
                // ~250 should be "a", ~250 should be silence.
                withClue("aCount") { aCount shouldBeInRange 200..300 }
                withClue("silenceCount") { silenceCount shouldBeInRange 200..300 }
            }
        }
    }
}
