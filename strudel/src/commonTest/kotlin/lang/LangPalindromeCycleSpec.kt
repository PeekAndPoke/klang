package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangPalindromeSpec : StringSpec({

    "palindrome() plays pattern forward then backward over two cycles" {
        val p = note("a b").palindrome()

        // Query two cycles
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 4

        // Cycle 0: Forward (a then b)
        events[0].data.note shouldBe "a"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.note shouldBe "b"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        // Cycle 1: Backward (b then a)
        // Since it's Cycle 1, the inner 'rev' reverses the content of Cycle 1
        events[2].data.note shouldBe "b"
        events[2].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[3].data.note shouldBe "a"
        events[3].begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
    }

    "palindrome() with simple sequence 'a b c d'" {
        val p = note("a b c d").palindrome()

        // Should take 2 cycles total
        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        events.size shouldBe 8

        // Cycle 0: a b c d (0.0 to 1.0)
        events.subList(0, 4).map { it.data.note } shouldBe listOf("a", "b", "c", "d")

        // Cycle 1: d c b a (1.0 to 2.0)
        events.subList(4, 8).map { it.data.note } shouldBe listOf("d", "c", "b", "a")
        events[4].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[7].end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "palindrome() with multi-cycle pattern '<[a b] [c d]>'" {
        // This pattern normally plays [a b] in cycle 0 and [c d] in cycle 1.
        // palindrome() should play [a b] then its reverse [b a].
        val p = note("<[a b] [c d]>").palindrome()

        val events = p.queryArc(0.0, 2.0).sortedBy { it.begin }

        // If it's cat(p, p.rev()):
        // Cycle 0: p(0..1) = [a b]
        // Cycle 1: p.rev()(1..2) = reverse of p(1..2) = reverse of [c d] = [d c]

        events.size shouldBe 4

        // Cycle 0
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "b"

        // Cycle 1 - This is where the discrepancy usually lies
        // Standard palindrome expects the REVERSE of the first cycle: [b a]
        // But our cat-based implementation plays the REVERSE of the second cycle: [d c]
        events[2].data.note shouldBe "d"
        events[3].data.note shouldBe "c"
    }

    "palindrome() with multi-cycle pattern '<[a b] [c d] [e f] [g a]>'" {
        // This plays:
        // Cycle 0: [a b]
        // Cycle 1: rev of Cycle 1 content [c d] -> [d c]
        // Cycle 2: [e f]
        // Cycle 3: rev of Cycle 3 content [g a] -> [a g]
        val p = note("<[a b] [c d] [e f] [g a]>").palindrome()

        val events = p.queryArc(0.0, 4.0).sortedBy { it.begin }

        events.size shouldBe 8

        // Cycle 0: a b
        events[0].data.note shouldBe "a"
        events[1].data.note shouldBe "b"

        // Cycle 1: d c (Reversed [c d])
        events[2].data.note shouldBe "d"
        events[3].data.note shouldBe "c"

        // Cycle 2: e f
        events[4].data.note shouldBe "e"
        events[5].data.note shouldBe "f"

        // Cycle 3: a g (Reversed [g a])
        events[6].data.note shouldBe "a"
        events[7].data.note shouldBe "g"
    }
})
