package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

class WeightedPatternSpec : StringSpec({

    "WeightedPattern: Direct Instantiation" {
        val inner = AtomicPattern(VoiceData.empty.copy(note = "a"))
        val pattern = WeightedPattern(inner, 3.5)

        // Verify that the weight property is explicitly set to the provided value
        pattern.weight shouldBe (3.5 plusOrMinus EPSILON)

        // Verify that events are passed through from the inner pattern without modification
        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBeEqualIgnoringCase "a"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "WeightedPattern: Kotlin DSL (mini-notation '@')" {
        // "a@2" in mini-notation creates a WeightedPattern with weight 2.0
        val pattern = note("a@2")

        pattern.weight shouldBe (2.0 plusOrMinus EPSILON)

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBeEqualIgnoringCase "a"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "WeightedPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("a@5.0")""")

        pattern.shouldNotBeNull()
        pattern.weight shouldBe (5.0 plusOrMinus EPSILON)

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBeEqualIgnoringCase "a"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
