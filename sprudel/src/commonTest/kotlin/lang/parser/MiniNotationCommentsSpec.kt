package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.lang.note

/**
 * Tests for comment handling in Mini-Notation
 *
 * Issue: // comments should be supported both at the beginning of lines
 * and in the middle of lines
 */
class MiniNotationCommentsSpec : StringSpec() {

    fun parse(input: String) = parseMiniNotation(input) { text, _ -> note(text) }

    init {
        "should handle comment at end of line" {
            val pattern = parse("c3 e3 // this is a comment")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "should handle comment after pattern with division operator" {
            val pattern = parse("c3/2 // divide by 2")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                events.size shouldBe 1
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                // Pattern is slowed by 2, so it takes 2 cycles
                events[0].whole.end.toDouble() shouldBe 2.0
            }
        }

        "should handle comment at beginning of line in multiline pattern" {
            val pattern = parse(
                """
                c3 e3
                // this is a comment
                g3
            """.trimIndent()
            )
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                // c3, e3, g3 - sequence of 3 notes
                events.size shouldBe 3
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
                events[2].data.note shouldBeEqualIgnoringCase "g3"
            }
        }

        "should handle multiple slashes in comment" {
            val pattern = parse("c3 e3 //////// lots of slashes")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "should handle comment with spaces before it" {
            val pattern = parse("c3 e3    // comment with leading spaces")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "should handle comment in sequence with brackets" {
            val pattern = parse("[c3 e3] // sequence with comment")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "should handle comment in alternation" {
            val pattern = parse("<c3 e3> // alternation with comment")
            val events = pattern.queryArc(0.0, 2.0)

            assertSoftly {
                // Alternation: c3 in cycle 0, e3 in cycle 1
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "should handle comment after multiplication operator" {
            val pattern = parse("c3*2 // multiply by 2")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                // Pattern is sped up by 2, so two occurrences in one cycle
                events.size shouldBe 2
                events[0].data.note shouldBeEqualIgnoringCase "c3"
                events[1].data.note shouldBeEqualIgnoringCase "c3"
            }
        }

        "should handle real-world pattern from user example" {
            val pattern = parse("[e5 [b4 c5] d5 [c5 b4]]    [a4 [a4 c5] e5 [d5 c5]]     //////// //////// ////////")
            val events = pattern.queryArc(0.0, 1.0)

            assertSoftly {
                // Should parse the pattern correctly, ignoring the comment slashes
                events.size shouldBe 12 // 6 notes in first group + 6 in second group
                events[0].data.note shouldBeEqualIgnoringCase "e5"
            }
        }
    }
}
