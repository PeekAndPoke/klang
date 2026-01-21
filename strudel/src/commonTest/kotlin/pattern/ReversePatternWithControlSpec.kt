package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceData

class ReversePatternWithControlSpec : StringSpec({

    "ReversePatternWithControl with static n=1 reverses normally" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
            )
        )
        // Control pattern that always returns 1
        val control = AtomicPattern(StrudelVoiceData.empty.copy(value = 1.asVoiceValue()))
        val reversed = ReversePatternWithControl(inner, control)

        val events = reversed.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "b"
        events[1].data.note shouldBe "a"
    }

    "ReversePatternWithControl with n=2 applies multi-cycle reversal" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
            )
        )
        // Control pattern that always returns 2
        val control = AtomicPattern(StrudelVoiceData.empty.copy(value = 2.asVoiceValue()))
        val reversed = ReversePatternWithControl(inner, control)

        val events = reversed.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should have events (multi-cycle reversal behavior)
        events.size shouldBe 2
        // First event should be from the reversed 2-cycle pattern
        events[0].data.note shouldBe "b"
        events[1].data.note shouldBe "a"
    }

    "ReversePatternWithControl with discrete pattern control" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "x")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "y"))
            )
        )
        // Control pattern: "1 2" - first half n=1, second half n=2
        val control = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(value = 1.asVoiceValue())),
                AtomicPattern(StrudelVoiceData.empty.copy(value = 2.asVoiceValue()))
            )
        )
        val reversed = ReversePatternWithControl(inner, control)

        val events = reversed.queryArc(0.0, 1.0)

        // Should have 2 events (one per control event)
        events.size shouldBe 2
    }

    "ReversePatternWithControl with n=0 applies normal reversal" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
            )
        )
        // Control pattern with n=0 (should behave like n=1)
        val control = AtomicPattern(StrudelVoiceData.empty.copy(value = 0.asVoiceValue()))
        val reversed = ReversePatternWithControl(inner, control)

        val events = reversed.queryArc(0.0, 1.0).sortedBy { it.begin }

        // Should still reverse (n<=1 behavior)
        events.size shouldBe 2
        events[0].data.note shouldBe "b"
        events[1].data.note shouldBe "a"
    }

    "ReversePatternWithControl returns empty when control pattern has no events" {
        val inner = SequencePattern(
            listOf(
                AtomicPattern(StrudelVoiceData.empty.copy(note = "a")),
                AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
            )
        )
        // Empty control pattern
        val control = EmptyPattern
        val reversed = ReversePatternWithControl(inner, control)

        val events = reversed.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "ReversePatternWithControl preserves weight from inner pattern" {
        val inner = AtomicPattern(StrudelVoiceData.empty.copy(note = "test"))
        val control = AtomicPattern(StrudelVoiceData.empty.copy(value = 1.asVoiceValue()))
        val reversed = ReversePatternWithControl(inner, control)

        reversed.weight shouldBe inner.weight
    }
})
