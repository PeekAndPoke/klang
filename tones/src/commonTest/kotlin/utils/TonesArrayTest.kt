package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesArrayTest : StringSpec({
    "range" {
        TonesArray.range(-2, 2) shouldBe listOf(-2, -1, 0, 1, 2)
        TonesArray.range(2, -2) shouldBe listOf(2, 1, 0, -1, -2)
    }

    "rotate" {
        TonesArray.rotate(2, "a b c d e".split(" ")) shouldBe "c d e a b".split(" ")
    }

    "compact" {
        val input = listOf("a", 1, 0, true, null, null)
        val result = listOf("a", 1, 0, true)
        TonesArray.compact(input) shouldBe result
    }

    "shuffle" {
        val rnd = { 0.2 }
        TonesArray.shuffle("a b c d".split(" "), rnd) shouldBe listOf("b", "c", "d", "a")
    }

    "permutations" {
        TonesArray.permutations(listOf("a", "b", "c")) shouldBe listOf(
            listOf("a", "b", "c"),
            listOf("b", "a", "c"),
            listOf("b", "c", "a"),
            listOf("a", "c", "b"),
            listOf("c", "a", "b"),
            listOf("c", "b", "a"),
        )
    }
})
