package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.stack

class StackPatternSpec : StringSpec({

    "StackPattern: Direct Instantiation" {
        val p1 = AtomicPattern(StrudelVoiceData.empty.copy(note = "a"))
        val p2 = AtomicPattern(StrudelVoiceData.empty.copy(note = "b"))
        val pattern = StackPattern(listOf(p1, p2))

        // We sort by begin in verifyPattern, but notes "a" and "b" both start at 0.0.
        // We just check that both are present.
        verifyPattern(pattern, 2) { _, note, begin, dur ->
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
            listOf("a", "b") shouldContain note
        }
    }

    "StackPattern: Kotlin DSL (stack function)" {
        val pattern = stack(note("a"), note("b"))

        verifyPattern(pattern, 2) { _, note, begin, dur ->
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
            listOf("a", "b") shouldContain note?.lowercase()
        }
    }

    "StackPattern: Kotlin DSL (mini-notation comma)" {
        // In mini-notation, commas create a stack
        val pattern = note("a, b")

        verifyPattern(pattern, 2) { _, note, begin, dur ->
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
            listOf("a", "b") shouldContain note?.lowercase()
        }
    }

    "StackPattern: Compiled Code" {
        val pattern = StrudelPattern.compile("""stack(note("a"), note("b"))""")

        verifyPattern(pattern, 2) { _, note, begin, dur ->
            begin shouldBe (0.0 plusOrMinus EPSILON)
            dur shouldBe (1.0 plusOrMinus EPSILON)
            listOf("a", "b") shouldContain note?.lowercase()
        }
    }
})
