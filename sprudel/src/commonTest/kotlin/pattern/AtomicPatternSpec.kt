package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.note

class AtomicPatternSpec : StringSpec({

    "AtomicPattern: Direct Instantiation" {
        val pattern = AtomicPattern(SprudelVoiceData.empty.copy(note = "c3"))

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBe "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "AtomicPattern: Kotlin DSL (note primitive)" {
        val pattern = note("c3")

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBeEqualIgnoringCase "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "AtomicPattern: Compiled Code" {
        val pattern = SprudelPattern.compile("""note("c3")""")

        verifyPattern(pattern, 1) { _, note, begin, dur ->
            note shouldBeEqualIgnoringCase "c3"
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
