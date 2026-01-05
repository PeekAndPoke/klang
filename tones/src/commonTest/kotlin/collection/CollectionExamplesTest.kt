package io.peekandpoke.klang.tones.collection

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CollectionExamplesTest : StringSpec({
    "Collection.range" {
        TonalArray.range(-2, 2) shouldBe listOf(-2, -1, 0, 1, 2)
        TonalArray.range(2, -2) shouldBe listOf(2, 1, 0, -1, -2)
    }

    "Collection.rotate" {
        TonalArray.rotate(1, listOf(1, 2, 3)) shouldBe listOf(2, 3, 1)
        TonalArray.rotate(-1, listOf(1, 2, 3)) shouldBe listOf(3, 1, 2)
    }

    "Collection.shuffle" {
        val list = listOf("a", "b", "c")
        val shuffled = TonalArray.shuffle(list)
        shuffled.size shouldBe 3
        shuffled.containsAll(list) shouldBe true
    }

    "Collection.permutations" {
        TonalArray.permutations(listOf("a", "b", "c")).size shouldBe 6
    }
})
