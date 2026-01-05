package io.peekandpoke.klang.tones.voicing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoicingTest : StringSpec({
    "C major triad inversions" {
        searchVoicings("C", listOf("C3", "C5"), VoicingDictionaries.triads) shouldBe listOf(
            listOf("C3", "E3", "G3"),
            listOf("C4", "E4", "G4"),
            listOf("E3", "G3", "C4"),
            listOf("E4", "G4", "C5"),
            listOf("G3", "C4", "E4")
        )
    }

    "C^7 lefthand" {
        searchVoicings("C^7", listOf("E3", "D5"), VoicingDictionaries.lefthand) shouldBe listOf(
            listOf("E3", "G3", "B3", "D4"),
            listOf("E4", "G4", "B4", "D5"),
            listOf("B3", "D4", "E4", "G4")
        )
    }

    "getVoicing Dm7" {
        getVoicing(
            "Dm7",
            listOf("F3", "A4"),
            VoicingDictionaries.lefthand,
            VoiceLeading.topNoteDiff
        ) shouldBe listOf("F3", "A3", "C4", "E4")
    }

    "getVoicing Dm7 with lastVoicing" {
        getVoicing(
            "Dm7",
            listOf("F3", "A4"),
            VoicingDictionaries.lefthand,
            VoiceLeading.topNoteDiff,
            listOf("C4", "E4", "G4", "B4")
        ) shouldBe listOf("C4", "E4", "F4", "A4")
    }

    "sequenceVoicings" {
        sequenceVoicings(
            listOf("C", "F", "G"),
            listOf("F3", "A4"),
            VoicingDictionaries.triads,
            VoiceLeading.topNoteDiff
        ) shouldBe listOf(
            listOf("C4", "E4", "G4"),
            listOf("A3", "C4", "F4"),
            listOf("B3", "D4", "G4")
        )
    }
})
