package io.peekandpoke.klang.tones.collection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CollectionTest : StringSpec({
    "range" {
        TonalArray.range(-2, 2) shouldBe listOf(-2, -1, 0, 1, 2)
        TonalArray.range(2, -2) shouldBe listOf(2, 1, 0, -1, -2)
    }

    "rotate" {
        TonalArray.rotate(2, "a b c d e".split(" ")) shouldBe "c d e a b".split(" ")
    }

    "compact" {
        val input = listOf("a", 1, 0, true, null, null)
        val result = listOf("a", 1, 0, true)
        TonalArray.compact(input) shouldBe result
    }

    "shuffle" {
        val rnd = { 0.2 }
        TonalArray.shuffle("a b c d".split(" "), rnd) shouldBe listOf("b", "c", "d", "a")
    }

    "permutations" {
        TonalArray.permutations(listOf("a", "b", "c")) shouldBe listOf(
            listOf("a", "b", "c"),
            listOf("b", "a", "c"),
            listOf("b", "c", "a"),
            listOf("a", "c", "b"),
            listOf("c", "a", "b"),
            listOf("c", "b", "a"),
        )
    }
})
