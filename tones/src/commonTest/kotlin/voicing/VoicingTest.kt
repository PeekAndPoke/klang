package io.peekandpoke.klang.tones.voicing

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VoicingTest : StringSpec({
    "C major triad inversions" {
        Voicing.search("C", listOf("C3", "C5"), VoicingDictionaries.triads) shouldBe listOf(
            listOf("C3", "E3", "G3"),
            listOf("C4", "E4", "G4"),
            listOf("E3", "G3", "C4"),
            listOf("E4", "G4", "C5"),
            listOf("G3", "C4", "E4")
        )
    }

    "C^7 lefthand" {
        Voicing.search("C^7", listOf("E3", "D5"), VoicingDictionaries.lefthand) shouldBe listOf(
            listOf("E3", "G3", "B3", "D4"),
            listOf("E4", "G4", "B4", "D5"),
            listOf("B3", "D4", "E4", "G4")
        )
    }

    "getVoicing Dm7" {
        Voicing.get(
            "Dm7",
            listOf("F3", "A4"),
            VoicingDictionaries.lefthand,
            VoiceLeading.topNoteDiff
        ) shouldBe listOf("F3", "A3", "C4", "E4")
    }

    "getVoicing Dm7 with lastVoicing" {
        Voicing.get(
            "Dm7",
            listOf("F3", "A4"),
            VoicingDictionaries.lefthand,
            VoiceLeading.topNoteDiff,
            listOf("C4", "E4", "G4", "B4")
        ) shouldBe listOf("C4", "E4", "F4", "A4")
    }

    "getRanked — common chord returns multiple candidates, rank[0] matches get()" {
        val ranked = Voicing.getRanked("C", listOf("C3", "C5"), VoicingDictionaries.triads)
        ranked shouldHaveAtLeastSize 2
        ranked.first() shouldBe Voicing.get("C", listOf("C3", "C5"), VoicingDictionaries.triads)
        ranked[1] shouldNotBe ranked[0]
    }

    "getRanked — fallback when range too tight returns chord-from-intervals at octave 4" {
        val ranked = Voicing.getRanked("C", listOf("C4", "C4"))
        ranked shouldBe listOf(listOf("C4", "E4", "G4"))
    }

    "getRanked — fallback for chord shape outside the dictionary" {
        // Use an unusual chord type that the default triads dictionary won't have entries for.
        // The dictionary search returns empty, but Chord.get can still produce intervals.
        val ranked = Voicing.getRanked("Cmaj7", listOf("C4", "C4"), VoicingDictionaries.triads)
        ranked.size shouldBe 1
        ranked.first() shouldBe listOf("C4", "E4", "G4", "B4")
    }

    "getRanked — invalid chord string returns empty" {
        Voicing.getRanked("XYZ123") shouldBe emptyList()
        Voicing.getRanked("") shouldBe emptyList()
    }

    "getRanked — guarantees ≥1 voicing across common chord shapes" {
        listOf("C", "Cm", "Cmaj7", "Cm7", "Cdim", "Caug", "Csus2", "Csus4", "C9", "C13").forEach { name ->
            val ranked = Voicing.getRanked(name)
            withClue("chord=$name") { ranked shouldNotBe emptyList<List<String>>() }
        }
    }

    "sequenceVoicings" {
        Voicing.sequence(
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
