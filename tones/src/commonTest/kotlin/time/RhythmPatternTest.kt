package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class RhythmPatternTest : StringSpec({
    "rhythmBinary" {
        Rhythm.binary(13) shouldBe listOf(1, 1, 0, 1)
        Rhythm.binary(12, 13) shouldBe listOf(1, 1, 0, 0, 1, 1, 0, 1)
    }

    "rhythmHex" {
        Rhythm.hex("8f") shouldBe listOf(1, 0, 0, 0, 1, 1, 1, 1)
    }

    "rhythmOnsets" {
        Rhythm.onsets(1, 2, 2, 1) shouldBe listOf(1, 0, 1, 0, 0, 1, 0, 0, 1, 0)
    }

    "rhythmRandom" {
        Rhythm.random(10) shouldHaveSize 10

        var current = 0.25
        val rnd = {
            current += 0.1
            current
        }
        Rhythm.random(5, 0.5, rnd) shouldBe listOf(0, 0, 1, 1, 1)
    }

    "rhythmProbability" {
        val rnd = { 0.5 }
        Rhythm.probability(listOf(0.5, 0.2, 0.0, 1.0, 0.0), rnd) shouldBe listOf(1, 0, 0, 1, 0)
    }

    "rhythmRotate" {
        Rhythm.rotate(listOf(1, 0, 0, 1), 0) shouldBe listOf(1, 0, 0, 1)
        Rhythm.rotate(listOf(1, 0, 0, 1), 1) shouldBe listOf(1, 1, 0, 0)
        Rhythm.rotate(listOf(1, 0, 0, 1), 2) shouldBe listOf(0, 1, 1, 0)
        Rhythm.rotate(listOf(1, 0, 0, 1), 3) shouldBe listOf(0, 0, 1, 1)
        Rhythm.rotate(listOf(1, 0, 0, 1), 4) shouldBe listOf(1, 0, 0, 1)
        Rhythm.rotate(listOf(1, 0, 0, 1), -1) shouldBe listOf(0, 0, 1, 1)
        Rhythm.rotate(listOf(1, 0, 0, 1), -2) shouldBe listOf(0, 1, 1, 0)
    }

    "rhythmEuclid" {
        Rhythm.euclid(8, 3) shouldBe listOf(1, 0, 0, 1, 0, 0, 1, 0)
    }
})
