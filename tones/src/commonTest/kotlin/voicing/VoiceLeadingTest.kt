package io.peekandpoke.klang.tones.voicing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoiceLeadingTest : StringSpec({
    "topNoteDiff" {
        VoiceLeading.topNoteDiff(
            listOf(
                listOf("F3", "A3", "C4", "E4"),
                listOf("C4", "E4", "F4", "A4")
            ),
            listOf("C4", "E4", "G4", "B4")
        ) shouldBe listOf("C4", "E4", "F4", "A4")
    }

    "topNoteDiffRanked — empty lastVoicing returns input order" {
        val voicings = listOf(
            listOf("C3", "E3", "G3"),
            listOf("E3", "G3", "C4"),
            listOf("G3", "C4", "E4")
        )
        VoiceLeading.topNoteDiffRanked(voicings, emptyList()) shouldBe voicings
    }

    "topNoteDiffRanked — sorts ascending by top-note distance" {
        val voicings = listOf(
            listOf("F3", "A3", "C4", "E4"),  // top E4, midi 64
            listOf("C4", "E4", "F4", "A4"),  // top A4, midi 69
            listOf("D4", "F4", "A4", "C5"),  // top C5, midi 72
        )
        // last top note: B4 = midi 71 → diffs are |71-64|=7, |71-69|=2, |71-72|=1
        VoiceLeading.topNoteDiffRanked(voicings, listOf("C4", "E4", "G4", "B4")) shouldBe listOf(
            listOf("D4", "F4", "A4", "C5"),
            listOf("C4", "E4", "F4", "A4"),
            listOf("F3", "A3", "C4", "E4"),
        )
    }

    "topNoteDiffRanked — first element matches topNoteDiff" {
        val voicings = listOf(
            listOf("F3", "A3", "C4", "E4"),
            listOf("C4", "E4", "F4", "A4")
        )
        val last = listOf("C4", "E4", "G4", "B4")
        VoiceLeading.topNoteDiffRanked(voicings, last).first() shouldBe
                VoiceLeading.topNoteDiff(voicings, last)
    }
})
