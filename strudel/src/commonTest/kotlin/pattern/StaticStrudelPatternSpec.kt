package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.makeStatic

class StaticStrudelPatternSpec : StringSpec({

    "StaticStrudelPattern: Direct Instantiation" {
        // Manually create two events in a 1-cycle window
        val events = listOf(
            StrudelPatternEvent(0.0, 0.5, 0.5, VoiceData.empty.copy(note = "a")),
            StrudelPatternEvent(0.5, 1.0, 0.5, VoiceData.empty.copy(note = "b"))
        )
        val pattern = StaticStrudelPattern(events)

        // It should implement Fixed, so weight is 1.0
        pattern.weight shouldBe (1.0 plusOrMinus EPSILON)

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
            }
            dur shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "StaticStrudelPattern: query multiple cycles (looping)" {
        val events = listOf(
            StrudelPatternEvent(0.0, 1.0, 1.0, VoiceData.empty.copy(note = "kick"))
        )
        val pattern = StaticStrudelPattern(events)

        // Query cycle 5
        val result = pattern.queryArc(5.0, 6.0)
        result.size shouldBe 1
        result[0].begin shouldBe (5.0 plusOrMinus EPSILON)
        result[0].data.note shouldBe "kick"
    }

    "StaticStrudelPattern: creation via makeStatic helper" {
        // Create a sequence and "freeze" it using makeStatic
        val source = note("a b c d")
        val frozen = source.makeStatic(0.0, 1.0)

        frozen shouldBe io.kotest.matchers.types.beInstanceOf<StaticStrudelPattern>()

        // Query the frozen pattern at a different cycle offset
        val events = frozen.queryArc(10.0, 11.0).sortedBy { it.begin }

        events.size shouldBe 4
        events[0].data.note shouldBe "a"
        events[0].begin shouldBe (10.0 plusOrMinus EPSILON)
        events[3].data.note shouldBe "d"
        events[3].begin shouldBe (10.75 plusOrMinus EPSILON)
    }
})
