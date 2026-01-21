package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.makeStatic
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class StaticStrudelPatternSpec : StringSpec({

    "StaticStrudelPattern: Direct Instantiation" {
        // Manually create two events in a 1-cycle window
        val events = listOf(
            StrudelPatternEvent(
                begin = 0.0.toRational(),
                end = 0.5.toRational(),
                dur = 0.5.toRational(),
                data = StrudelVoiceData.empty.copy(note = "a"),
            ),
            StrudelPatternEvent(
                begin = 0.5.toRational(),
                end = 1.0.toRational(),
                dur = 0.5.toRational(),
                data = StrudelVoiceData.empty.copy(note = "b"),
            )
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
            StrudelPatternEvent(
                begin = 0.0.toRational(),
                end = 1.0.toRational(),
                dur = 1.0.toRational(),
                data = StrudelVoiceData.empty.copy(note = "kick"),
            )
        )
        val pattern = StaticStrudelPattern(events)

        // Query cycle 5
        val result = pattern.queryArc(5.0, 6.0)
        result.size shouldBe 1
        result[0].begin.toDouble() shouldBe (5.0 plusOrMinus EPSILON)
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
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin.toDouble() shouldBe (10.0 plusOrMinus EPSILON)
        events[3].data.note shouldBeEqualIgnoringCase "d"
        events[3].begin.toDouble() shouldBe (10.75 plusOrMinus EPSILON)
    }
})
