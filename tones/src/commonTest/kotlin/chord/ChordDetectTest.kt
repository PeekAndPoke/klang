package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChordDetectTest : StringSpec({
    "detect chords" {
        detectChord(listOf("D", "F#", "A", "C")) shouldBe listOf("D7")
        detectChord(listOf("F#", "A", "C", "D")) shouldBe listOf("D7/F#")
        detectChord(listOf("A", "C", "D", "F#")) shouldBe listOf("D7/A")
        detectChord(listOf("E", "G#", "B", "C#")) shouldBe listOf("E6", "C#m7/E")
    }

    "assume perfect 5th" {
        detectChord(listOf("D", "F", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf("Dm7")
        detectChord(listOf("D", "F", "C"), DetectOptions(assumePerfectFifth = false)) shouldBe emptyList()
        detectChord(listOf("D", "F", "A", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf("Dm7", "F6/D")
        detectChord(listOf("D", "F", "A", "C"), DetectOptions(assumePerfectFifth = false)) shouldBe listOf(
            "Dm7",
            "F6/D"
        )
        detectChord(listOf("D", "F", "Ab", "C"), DetectOptions(assumePerfectFifth = true)) shouldBe listOf(
            "Dm7b5",
            "Fm6/D"
        )
    }

    "(regression) detect aug" {
        detectChord(listOf("C", "E", "G#")) shouldBe listOf("Caug", "Eaug/C", "G#aug/C")
    }

    "edge cases" {
        detectChord(emptyList()) shouldBe emptyList()
    }
})
