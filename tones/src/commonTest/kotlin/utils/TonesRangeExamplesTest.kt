package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesRangeExamplesTest : StringSpec({
    "TonesRange.numeric" {
        TonesRange.numeric(listOf(10, 5)) shouldBe listOf(10, 9, 8, 7, 6, 5)
        TonesRange.numeric(listOf(-5, 5)) shouldBe listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
        TonesRange.numeric(listOf("C5", "C4")) shouldBe listOf(72, 71, 70, 69, 68, 67, 66, 65, 64, 63, 62, 61, 60)
        TonesRange.numeric(listOf("C4", "E4", "Bb3")) shouldBe listOf(60, 61, 62, 63, 64, 63, 62, 61, 60, 59, 58)
    }

    "TonesRange.chromatic" {
        TonesRange.chromatic(listOf("C2", "E2", "D2")) shouldBe
                listOf("C2", "Db2", "D2", "Eb2", "E2", "Eb2", "D2")

        TonesRange.chromatic(listOf("C2", "C3"), sharps = true) shouldBe
                listOf("C2", "C#2", "D2", "D#2", "E2", "F2", "F#2", "G2", "G#2", "A2", "A#2", "B2", "C3")
    }
})
