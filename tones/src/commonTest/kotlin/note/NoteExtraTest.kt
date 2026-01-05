package io.peekandpoke.klang.tones.note

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.peekandpoke.klang.tones.distance.Distance

class NoteExtraTest : StringSpec({
    "fromMidi" {
        Note.fromMidi(60) shouldBe "C4"
        Note.fromMidi(61) shouldBe "Db4"
        Note.fromMidi(61, sharps = true) shouldBe "C#4"
        Note.fromMidi(66) shouldBe "Gb4"
        Note.fromMidi(66, sharps = true) shouldBe "F#4"
        Note.fromMidi(21) shouldBe "A0"
        Note.fromMidi(108) shouldBe "C8"
        Note.fromMidi(60, pitchClass = true) shouldBe "C"
        Note.fromMidi(61, pitchClass = true) shouldBe "Db"
    }

    "enharmonic" {
        println("[DEBUG_LOG] enharmonic(\"C4\", \"B#3\") -> ${Note.enharmonic("C4", "B#3")}")
        Note.enharmonic("C4", "B#3") shouldBe "B#3"
        Note.enharmonic("B#3", "C4") shouldBe "C4"
        Note.enharmonic("F4", "E#4") shouldBe "E#4"
        Note.enharmonic("E#4", "F4") shouldBe "F4"
        Note.enharmonic("C#4", "Db4") shouldBe "Db4"
        Note.enharmonic("Db4", "C#4") shouldBe "C#4"

        // Pitch classes (no octave)
        Note.enharmonic("C", "B#") shouldBe "B#"
        Note.enharmonic("B#", "C") shouldBe "C"

        // Same note name
        Note.enharmonic("C4", "C") shouldBe "C4"

        // Invalid or non-matching
        Note.enharmonic("C4", "D4") shouldBe ""
        Note.enharmonic("C4", "G") shouldBe ""
    }

    "sortedUniqNames" {
        Note.sortedUniqNames(listOf("c2", "d3", "c5", "c4")) shouldBe listOf("C", "D")
        Note.sortedUniqNames(listOf("f", "a", "c", "e", "g", "b", "d")) shouldBe listOf(
            "C",
            "D",
            "E",
            "F",
            "G",
            "A",
            "B"
        )
        Note.sortedUniqNames(listOf("c#", "db", "d")) shouldBe listOf("C#", "Db", "D")
    }

    "sortedNoteNames" {
        Note.sortedNames(listOf("c2", "c5", "c1", "c0", "c6", "c")) shouldBe listOf("C", "C0", "C1", "C2", "C5", "C6")
    }

    "sortedUniqNoteNames" {
        Note.sortedUniqNoteNames(listOf("a", "b", "c2", "1p", "p2", "c2", "b", "c", "c3")) shouldBe listOf(
            "C",
            "A",
            "B",
            "C2",
            "C3"
        )
    }

    "simplify" {
        Note.simplify("C##") shouldBe "D"
        Note.simplify("C###") shouldBe "D#"
        Note.simplify("B#4") shouldBe "C5"
        Note.simplify("Cbb") shouldBe "Bb"
    }

    "transposeOctaves" {
        Distance.transposeOctaves("C4", 1) shouldBe "C5"
        Distance.transposeOctaves("C4", -1) shouldBe "C3"
        Distance.transposeOctaves("C", 1) shouldBe "C"
    }
})
