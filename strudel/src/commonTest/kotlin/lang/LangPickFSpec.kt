package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPickFSpec : StringSpec({

    "pickF() applies function based on index" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.fast(2) },
            { it.slow(2) },
            { it.rev() }
        )

        val pattern = note("c e g").pickF("0", functions)
        val events = pattern.queryArc(0.0, 1.0)

        // Function at index 0 is fast(2), so should have 6 events
        events.size shouldBe 6
    }

    "pickF() with different indices - transpose" {
        val functions = listOf<PatternMapper>(
            { it }, // identity
            { it.transpose(2) }
        )

        val pattern = note("c").pickF("0 1", functions)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // First half: index 0 (identity) = 1 event
        // Second half: index 1 (struct x*2) = 2 events
        // Total: 3 events
        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "pickF() with different indices" {
        val functions = listOf<PatternMapper>(
            { it }, // identity
            { it.add("1") }
        )

        val pattern = seq("0").pickF("0 1", functions)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // First half: index 0 (identity) = 1 event
        // Second half: index 1 (struct x*2) = 2 events
        // Total: 3 events
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "pickF() clamps index to list bounds" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.fast(2) },
            { it.fast(3) }
        )

        // Index 5 is out of bounds, should clamp to index 1 (last)
        val pattern = note("c").pickF("5", functions)
        val events = pattern.queryArc(0.0, 1.0)

        // Should use function at index 1: fast(3) = 3 events
        events.size shouldBe 3
    }

    "pickF() works as standalone function" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.fast(2) }
        )

        val pattern = pickF("0", functions, note("c"))
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 2
    }

    "pickF() works in compiled code" {
        val pattern = StrudelPattern.compile("""note("c e g").pickF("0", [x => x.fast(2)])""")
        val events = pattern?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 6
    }

    "pickmodF() wraps index with modulo" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.fast(2) },
            { it.fast(3) }
        )

        // Index 2 wraps to 0 (2 % 2 = 0)
        val pattern = note("c").pickmodF("2", functions)
        val events = pattern.queryArc(0.0, 1.0)

        // Should use function at index 0: fast(2) = 2 events
        events.size shouldBe 2
    }

    "pickmodF() handles large indices" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it }, // identity
            { it.fast(2) },
            { it.fast(3) }
        )

        // Index 7 wraps to 1 (7 % 3 = 1)
        val pattern = note("c").pickmodF("7", functions)
        val events = pattern.queryArc(0.0, 1.0)

        // Should use function at index 1: fast(2) = 2 events
        events.size shouldBe 2
    }

    "pickmodF() works as standalone function" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.fast(2) }
        )

        val pattern = pickmodF("5", functions, note("c"))
        val events = pattern.queryArc(0.0, 1.0)

        // Index 5 wraps to 0 (5 % 1 = 0)
        events.size shouldBe 2
    }

    "pickmodF() works with control pattern" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it }, // identity
            { it.add(1) }
        )

        val pattern = "0".pickmodF("0 1 2 3", functions)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Quarter 0: index 0 (identity) = 1 event
        // Quarter 1: index 1 (fast 2) = 2 events
        // Quarter 2: index 2 wraps to 0 (identity) = 1 event
        // Quarter 3: index 3 wraps to 1 (fast 2) = 2 events
        // Total: 6 events
        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
        events[3].data.value?.asInt shouldBe 1
    }

    "pickF() with rev function" {
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.rev() }
        )

        val pattern = note("c e g").pickF("0", functions)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // Reversed order
        events[0].data.note shouldBe "g"
        events[1].data.note shouldBe "e"
        events[2].data.note shouldBe "c"
    }

    "pickF() with add - matching compat test case" {
        // Direct Kotlin equivalent of: seq("2 4").pickF("<0 1>", [x => x.add(1), x => x.add(2)])
        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.add(1) },
            { it.add(2) }
        )

        val pattern = seq("2 4").pickF("<0 1>", functions)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            withClue("Should have 4 events total") {
                events.size shouldBe 2
            }

            withClue("Cycle 0 uses index 0, so add(1) is applied") {
                // Original seq("2 4") gives: [0, 0.5) value=2, [0.5, 1.0) value=4
                // With add(1): [0, 0.5) value=3, [0.5, 1.0) value=5
                events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
                events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
                events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)

                events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
                events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
                events[1].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
            }
        }
    }

    "pickF() with alternating indices - full cycle detail" {
        // Pattern: seq("2 4").pickF("<0 1>", [x => x.add(1), x => x.add(2)])
        // "<0 1>" alternates between 0 and 1 over cycles
        // Cycle 0: index 0 -> add(1) applied to seq("2 4") -> values 3, 5
        // Cycle 1: index 1 -> add(2) applied to seq("2 4") -> values 4, 6

        val functions = listOf<(StrudelPattern) -> StrudelPattern>(
            { it.add(1) },
            { it.add(2) }
        )

        val pattern = seq("2 4").pickF("<0 1>", functions)

        assertSoftly {
            // Cycle 0: add(1) applied
            val cycle0 = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }
            withClue("Cycle 0 with add(1)") {
                cycle0.size shouldBe 2
                cycle0[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
                cycle0[1].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
            }

            // Cycle 1: add(2) applied
            val cycle1 = pattern.queryArc(1.0, 2.0).sortedBy { it.part.begin }
            withClue("Cycle 1 with add(2)") {
                cycle1.size shouldBe 2
                cycle1[0].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)
                cycle1[1].data.value?.asDouble shouldBe (6.0 plusOrMinus EPSILON)
            }
        }
    }
})
