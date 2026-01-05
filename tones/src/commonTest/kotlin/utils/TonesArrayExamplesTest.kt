package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesArrayExamplesTest : StringSpec({
    "TonesArray.range" {
        TonesArray.range(-2, 2) shouldBe listOf(-2, -1, 0, 1, 2)
        TonesArray.range(2, -2) shouldBe listOf(2, 1, 0, -1, -2)
    }

    "TonesArray.rotate" {
        TonesArray.rotate(1, listOf(1, 2, 3)) shouldBe listOf(2, 3, 1)
        TonesArray.rotate(-1, listOf(1, 2, 3)) shouldBe listOf(3, 1, 2)
    }

    "TonesArray.shuffle" {
        val list = listOf("a", "b", "c")
        val shuffled = TonesArray.shuffle(list)
        shuffled.size shouldBe 3
        shuffled.containsAll(list) shouldBe true
    }

    "TonesArray.permutations" {
        TonesArray.permutations(listOf("a", "b", "c")).size shouldBe 6
    }
})
