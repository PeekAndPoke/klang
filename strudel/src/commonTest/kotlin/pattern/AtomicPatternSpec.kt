package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

class AtomicPatternSpec : StringSpec({

    "AtomicPattern: Direct Instantiation" {
        val pattern = AtomicPattern(VoiceData.empty.copy(note = "c3"))

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "AtomicPattern: Kotlin DSL (note primitive)" {
        val pattern = note("c3")

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "AtomicPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""note("c3")""")

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
