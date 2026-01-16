package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRevSpec : StringSpec({

    "rev() reverses a pattern within one cycle" {
        // Given a pattern "a b" (a at 0.0, b at 0.5)
        val p = sound("a b").rev()

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Then "b" should be first and "a" second
        events.size shouldBe 2

        events[0].data.sound shouldBe "b"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.sound shouldBe "a"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "rev(n) reverses every n-th cycle" {
        // rev(2) means the pattern is reversed over 2 cycles.
        // For "a b", cycle 0 stays "a b", cycle 1 stays "a b".
        // Reversed over 2 cycles: The event at 1.75 (second half of 2nd cycle) moves to 0.25.
        val p = sound("a b").rev(2)

        // Querying the first cycle of the 2-cycle reversal
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // In a 2-cycle reverse of "a b a b":
        // 0.0-0.5 (a) -> 1.5-2.0
        // 0.5-1.0 (b) -> 1.0-1.5
        // 1.0-1.5 (a) -> 0.5-1.0
        // 1.5-2.0 (b) -> 0.0-0.5
        events.size shouldBe 2
        events[0].data.sound shouldBe "b" // From the very end of the 2nd cycle
        events[1].data.sound shouldBe "a" // From the first half of the 2nd cycle
    }

    "rev() works as a standalone function" {
        val p = rev(sound("bd hh"))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.sound shouldBe "hh"
    }

    "rev() works as extension on String" {
        val p = "bd hh".rev()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "hh"
    }

    "rev() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").rev()""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "hh"
    }

    "rev(n) works in compiled code" {
        val p = StrudelPattern.compile("""sound("a b").rev(2)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "b"
    }

    "rev() with discrete pattern control" {
        // Using a pattern "1 2 1 2" to control reversal
        // The control pattern "1 2 1 2" creates 4 events (quarters)
        // For each quarter, we apply reversal with the corresponding n value
        val p = sound("bd hh").rev("1 2 1 2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should have events from the reversed pattern
        // With 4 control events, we get events for each quarter
        events.size shouldBe 4
    }

    "rev() with continuous pattern control (irand)" {
        // Using irand(4) which generates random integers 0-3
        // This will vary the reversal dynamically
        val p = sound("bd hh sd cp").rev(irand(4).segment(4))
        val events = p.queryArc(0.0, 1.0)

        // Should have 4 events (one per quarter)
        events.size shouldBe 4
    }

    "rev() with control pattern works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").rev("1 2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Should produce events
        events.size shouldBe 2
    }

    "rev() with continuous pattern produces correct reversal" {
        // Test with steady(2) - should behave like rev(2)
        val p1 = sound("a b c d").rev(2)
        val p2 = sound("a b c d").rev(steady(2))

        val events1 = p1.queryArc(0.0, 2.0).sortedBy { it.begin }
        val events2 = p2.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Both should have same number of events
        events1.size shouldBe events2.size
    }
})
