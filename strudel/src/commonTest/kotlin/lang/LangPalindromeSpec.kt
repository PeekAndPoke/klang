package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPalindromeSpec : StringSpec({

    "palindrome() plays pattern forward then backward over two cycles" {
        val p = note("a b").palindrome()

        // Query two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4

        // Cycle 0: Forward (a then b)
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "b"

        // Cycle 1: Backward (b then a)
        events[2].data.note shouldBeEqualIgnoringCase "b"
        events[3].data.note shouldBeEqualIgnoringCase "a"

        events[2].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "palindrome() with multi-cycle pattern '<[a b] [c d]>'" {
        // Implementation is cat(p, p.rev())
        // Cycle 0: p(0..1) -> [a b]
        // Cycle 1: p.rev()(1..2) -> reverse of p(1..2) -> reverse of [c d] -> [d c]
        val p = note("<[a b] [c d]>").palindrome()

        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events.map { it.data.note?.lowercase() } shouldBe listOf("a", "b", "d", "c")
    }

    "palindrome() works as a standalone function" {
        val p = palindrome(note("a b"))
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events.map { it.data.note?.lowercase() } shouldBe listOf("a", "b", "b", "a")
    }

    "palindrome() works as extension on String" {
        val p = "a b".palindrome()
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events.map { it.data.value?.asString } shouldBe listOf("a", "b", "b", "a")
    }

    "palindrome() works in compiled code" {
        val p = StrudelPattern.compile("""note("a b").palindrome()""")
        val events = p?.queryArc(0.0, 2.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 4
        events.map { it.data.note?.lowercase() } shouldBe listOf("a", "b", "b", "a")
    }
})
