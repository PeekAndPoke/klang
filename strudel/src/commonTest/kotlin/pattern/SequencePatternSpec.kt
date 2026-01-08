package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq

class SequencePatternSpec : StringSpec({

    "SequencePattern: Direct Instantiation" {
        val p1 = AtomicPattern(VoiceData.empty.copy(note = "a"))
        val p2 = AtomicPattern(VoiceData.empty.copy(note = "b"))
        val pattern = SequencePattern(listOf(p1, p2))

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Kotlin DSL (seq function)" {
        val pattern = seq(note("a"), note("b"))

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Kotlin DSL (mini-notation space)" {
        val pattern = note("a b")

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }

    "SequencePattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("a b")""")

        verifyPattern(pattern, 2) { i, note, begin, dur ->
            if (i == 0) {
                note shouldBe "a"
                begin shouldBe (0.0 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            } else {
                note shouldBe "b"
                begin shouldBe (0.5 plusOrMinus EPSILON)
                dur shouldBe (0.5 plusOrMinus EPSILON)
            }
        }
    }
})
