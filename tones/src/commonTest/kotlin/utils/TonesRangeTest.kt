package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesRangeTest : StringSpec({
    "numericRange" {
        TonesRange.numeric(emptyList()) shouldBe emptyList()
        TonesRange.numeric(listOf("C4")) shouldBe listOf(60)

        TonesRange.numeric(listOf(0, 10)) shouldBe listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        TonesRange.numeric(listOf(10, 0)) shouldBe listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        TonesRange.numeric(listOf(5, 0)) shouldBe listOf(5, 4, 3, 2, 1, 0)
        TonesRange.numeric(listOf(10, 5)) shouldBe listOf(10, 9, 8, 7, 6, 5)

        TonesRange.numeric(listOf(-5, 5)) shouldBe listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
        TonesRange.numeric(listOf(5, -5)) shouldBe listOf(5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5)
        TonesRange.numeric(listOf(5, -5, 0)) shouldBe listOf(5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -4, -3, -2, -1, 0)
        TonesRange.numeric(listOf(-5, -10)) shouldBe listOf(-5, -6, -7, -8, -9, -10)
        TonesRange.numeric(listOf(-10, -5)) shouldBe listOf(-10, -9, -8, -7, -6, -5)

        val r1 = listOf(60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72)
        TonesRange.numeric(listOf("C4", "C5")) shouldBe r1

        val r2 = listOf(72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60)
        TonesRange.numeric(listOf("C5", "C4")) shouldBe r2

        TonesRange.numeric("C2 F2 Bb1 C2".split(" ")) shouldBe listOf(
            36, 37, 38, 39, 40, 41, 40, 39, 38, 37, 36, 35, 34, 35, 36
        )
    }

    "chromaticRange" {
        TonesRange.chromatic(listOf("A3", "A4")) shouldBe
                "A3 Bb3 B3 C4 Db4 D4 Eb4 E4 F4 Gb4 G4 Ab4 A4".split(" ")

        TonesRange.chromatic(listOf("A4", "A3")) shouldBe
                "A4 Ab4 G4 Gb4 F4 E4 Eb4 D4 Db4 C4 B3 Bb3 A3".split(" ")

        TonesRange.chromatic("C3 Eb3 A2".split(" ")) shouldBe
                "C3 Db3 D3 Eb3 D3 Db3 C3 B2 Bb2 A2".split(" ")

        TonesRange.chromatic(listOf("C2", "C3"), sharps = true) shouldBe
                "C2 C#2 D2 D#2 E2 F2 F#2 G2 G#2 A2 A#2 B2 C3".split(" ")

        TonesRange.chromatic(listOf("C2", "C3"), sharps = true, pitchClass = true) shouldBe
                "C C# D D# E F F# G G# A A# B C".split(" ")
    }
})
