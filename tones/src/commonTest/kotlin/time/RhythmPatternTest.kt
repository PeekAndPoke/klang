package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class RhythmPatternTest : StringSpec({
    "rhythmBinary" {
        rhythmBinary(13) shouldBe listOf(1, 1, 0, 1)
        rhythmBinary(12, 13) shouldBe listOf(1, 1, 0, 0, 1, 1, 0, 1)
    }

    "rhythmHex" {
        rhythmHex("8f") shouldBe listOf(1, 0, 0, 0, 1, 1, 1, 1)
    }

    "rhythmOnsets" {
        rhythmOnsets(1, 2, 2, 1) shouldBe listOf(1, 0, 1, 0, 0, 1, 0, 0, 1, 0)
    }

    "rhythmRandom" {
        rhythmRandom(10) shouldHaveSize 10

        var current = 0.25
        val rnd = {
            current += 0.1
            current
        }
        rhythmRandom(5, 0.5, rnd) shouldBe listOf(0, 0, 1, 1, 1)
    }

    "rhythmProbability" {
        val rnd = { 0.5 }
        rhythmProbability(listOf(0.5, 0.2, 0.0, 1.0, 0.0), rnd) shouldBe listOf(1, 0, 0, 1, 0)
    }

    "rhythmRotate" {
        rhythmRotate(listOf(1, 0, 0, 1), 0) shouldBe listOf(1, 0, 0, 1)
        rhythmRotate(listOf(1, 0, 0, 1), 1) shouldBe listOf(1, 1, 0, 0)
        rhythmRotate(listOf(1, 0, 0, 1), 2) shouldBe listOf(0, 1, 1, 0)
        rhythmRotate(listOf(1, 0, 0, 1), 3) shouldBe listOf(0, 0, 1, 1)
        rhythmRotate(listOf(1, 0, 0, 1), 4) shouldBe listOf(1, 0, 0, 1)
        rhythmRotate(listOf(1, 0, 0, 1), -1) shouldBe listOf(0, 0, 1, 1)
        rhythmRotate(listOf(1, 0, 0, 1), -2) shouldBe listOf(0, 1, 1, 0)
    }

    "rhythmEuclid" {
        rhythmEuclid(8, 3) shouldBe listOf(1, 0, 0, 1, 0, 0, 1, 0)
    }
})
