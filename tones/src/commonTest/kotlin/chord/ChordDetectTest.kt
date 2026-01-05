package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChordDetectTest : StringSpec({
    "detect chords" {
        ChordDetect.detect(listOf("D", "F#", "A", "C")) shouldBe listOf("D7")
        ChordDetect.detect(listOf("F#", "A", "C", "D")) shouldBe listOf("D7/F#")
        ChordDetect.detect(listOf("A", "C", "D", "F#")) shouldBe listOf("D7/A")
        ChordDetect.detect(listOf("E", "G#", "B", "C#")) shouldBe listOf("E6", "C#m7/E")
    }

    "assume perfect 5th" {
        ChordDetect.detect(listOf("D", "F", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf("Dm7")
        ChordDetect.detect(listOf("D", "F", "C"), DetectOptions(assumePerfectFifth = false)) shouldBe emptyList()
        ChordDetect.detect(listOf("D", "F", "A", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf(
            "Dm7",
            "F6/D"
        )
        ChordDetect.detect(listOf("D", "F", "A", "C"), DetectOptions(assumePerfectFifth = false)) shouldBe listOf(
            "Dm7",
            "F6/D"
        )
        ChordDetect.detect(listOf("D", "F", "Ab", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf(
            "Dm7b5",
            "Fm6/D"
        )
    }

    "(regression) detect aug" {
        ChordDetect.detect(listOf("C", "E", "G#")) shouldBe listOf("Caug", "Eaug/C", "G#aug/C")
    }

    "edge cases" {
        ChordDetect.detect(emptyList()) shouldBe emptyList()
    }
})
