package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelVoiceData

class ReversePatternSpec : StringSpec({

    "ReversePattern reverses events within a single cycle" {
        // Create a simple sequence: event at 0.0-0.5 and 0.5-1.0
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
            )
        )
        val reversed = ReversePattern(inner)

        val events = reversed.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should have 2 events
        events.size shouldBe 2

        // First event should be "b" (from 0.5-1.0 reversed to 0.0-0.5)
        events[0].data.note shouldBe "b"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        // Second event should be "a" (from 0.0-0.5 reversed to 0.5-1.0)
        events[1].data.note shouldBe "a"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "ReversePattern reverses each cycle independently" {
        // Create pattern with events in cycle 0 and cycle 1
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "x")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "y"))
            )
        )
        val reversed = ReversePattern(inner)

        // Query two cycles
        val events = reversed.queryArc(0.0, 2.0).sortedBy { it.begin }

        // Should have 4 events (2 per cycle)
        events.size shouldBe 4

        // Cycle 0: y then x
        events[0].data.note shouldBe "y"
        events[1].data.note shouldBe "x"

        // Cycle 1: y then x (reversed independently)
        events[2].data.note shouldBe "y"
        events[3].data.note shouldBe "x"
    }

    "ReversePattern handles partial cycle queries" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "c")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "d"))
            )
        )
        val reversed = ReversePattern(inner)

        // Query only the middle portion (0.25 to 0.75)
        val events = reversed.queryArc(0.25, 0.75).sortedBy { it.begin }

        // Should get events that fall in this range after reversal
        // Original: a(0-0.25), b(0.25-0.5), c(0.5-0.75), d(0.75-1.0)
        // Reversed: d(0-0.25), c(0.25-0.5), b(0.5-0.75), a(0.75-1.0)
        // Query 0.25-0.75 should get: c(0.25-0.5), b(0.5-0.75)
        events.size shouldBe 2
        events[0].data.note shouldBe "c"
        events[1].data.note shouldBe "b"
    }

    "ReversePattern preserves event weight" {
        val inner = AtomicPattern(StrudelVoiceData.empty.copy(note = "test"))
        val reversed = ReversePattern(inner)

        reversed.weight shouldBe inner.weight
    }
})
