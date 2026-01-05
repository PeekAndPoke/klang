package io.peekandpoke.klang.tones.voicing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoicingExamplesTest : StringSpec({
    "Voicing.search" {
        val dict = mapOf("^7" to listOf("3M 5P 7M 9M", "7M 9M 10M 12P"))
        val voicings = searchVoicings("C^7", listOf("E3", "D5"), dict)
        voicings shouldBe listOf(
            listOf("E3", "G3", "B3", "D4"),
            listOf("E4", "G4", "B4", "D5"),
            listOf("B3", "D4", "E4", "G4")
        )
    }

    "Voicing.get" {
        val lefthand = VoicingDictionaries.lefthand
        val topNoteDiff = VoiceLeading.topNoteDiff

        getVoicing("Dm7", listOf("F3", "A4"), lefthand, topNoteDiff) shouldBe
                listOf("F3", "A3", "C4", "E4")

        val last = listOf("C4", "E4", "G4", "B4")
        getVoicing("Dm7", listOf("F3", "A4"), lefthand, topNoteDiff, last) shouldBe
                listOf("C4", "E4", "F4", "A4")
    }

    "Voicing.sequence" {
        val chords = listOf("Cmaj7", "Dm7", "G7", "Cmaj7")
        val sequence = sequenceVoicings(chords)
        sequence.size shouldBe 4
    }
})
