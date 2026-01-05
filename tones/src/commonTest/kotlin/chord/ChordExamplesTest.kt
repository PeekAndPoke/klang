package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.range.numericRange

class ChordExamplesTest : StringSpec({
    "Chord.get" {
        val c = chord("Cmaj7/B")
        c.empty shouldBe false
        c.symbol shouldBe "Cmaj7/B"
        c.tonic shouldBe "C"
        c.type shouldBe "major seventh"
        c.root shouldBe "B"
        c.bass shouldBe "B"
        c.rootDegree shouldBe 4
        c.notes shouldBe listOf("B", "C", "E", "G")
        c.intervals shouldBe listOf("7M", "8P", "10M", "12P")
    }

    "Chord.getChord" {
        getChord("maj7", "C", "B").symbol shouldBe chord("Cmaj7/B").symbol
    }

    "Chord.notes" {
        chordNotes("maj7", "C4") shouldBe listOf("C4", "E4", "G4", "B4")
    }

    "Chord.degrees" {
        val c4m7 = chordDegrees("m7", "C4")
        c4m7(1) shouldBe "C4"
        c4m7(2) shouldBe "Eb4"
        c4m7(3) shouldBe "G4"
        c4m7(4) shouldBe "Bb4"
        c4m7(5) shouldBe "C5"

        listOf(1, 2, 3, 4).map(c4m7) shouldBe listOf("C4", "Eb4", "G4", "Bb4")
        listOf(2, 3, 4, 5).map(c4m7) shouldBe listOf("Eb4", "G4", "Bb4", "C5")
        listOf(3, 4, 5, 6).map(c4m7) shouldBe listOf("G4", "Bb4", "C5", "Eb5")
        listOf(4, 5, 6, 7).map(c4m7) shouldBe listOf("Bb4", "C5", "Eb5", "G5")

        c4m7(0) shouldBe ""
    }

    "Chord.steps" {
        numericRange(listOf(-3, 3)).map(chordSteps("aug", "C4")) shouldBe
                listOf("C3", "E3", "G#3", "C4", "E4", "G#4", "C5")
    }

    "Chord.detect" {
        detectChord(listOf("D", "F#", "A", "C")) shouldBe listOf("D7")
        detectChord(listOf("F#", "A", "C", "D")) shouldBe listOf("D7/F#")
    }

    "Chord.transpose" {
        transposeChord("Eb7b9", "5P") shouldBe "Bb7b9"
    }

    "Chord.chordScales" {
        chordScales("C7b9") shouldBe
                listOf("phrygian dominant", "flamenco", "spanish heptatonic", "half-whole diminished", "chromatic")
    }

    "Chord.extended" {
        val extended = extendedChords("Cmaj7")
        // Note: TonalJS README has "Cmaj#4", "Cmaj7#9#11", etc.
        // Our ChordTypeDictionary aliases might be different.
        extended.take(5) shouldBe listOf("Cmaj#4", "Cmaj7#9#11", "Cmaj9", "Cmaj13", "CM7add13")
    }

    "Chord.reduced" {
        reducedChords("Cmaj7") shouldBe listOf("C5", "CM")
    }
})
