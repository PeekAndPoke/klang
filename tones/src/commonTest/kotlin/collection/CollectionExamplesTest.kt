package io.peekandpoke.klang.tones.collection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CollectionExamplesTest : StringSpec({
    "Collection.range" {
        range(-2, 2) shouldBe listOf(-2, -1, 0, 1, 2)
        range(2, -2) shouldBe listOf(2, 1, 0, -1, -2)
    }

    "Collection.rotate" {
        rotate(1, listOf(1, 2, 3)) shouldBe listOf(2, 3, 1)
        rotate(-1, listOf(1, 2, 3)) shouldBe listOf(3, 1, 2)
    }

    "Collection.shuffle" {
        val list = listOf("a", "b", "c")
        val shuffled = shuffle(list)
        shuffled.size shouldBe 3
        shuffled.containsAll(list) shouldBe true
    }

    "Collection.permutations" {
        permutations(listOf("a", "b", "c")).size shouldBe 6
    }
})
