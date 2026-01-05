package io.peekandpoke.klang.tones.note

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NoteExtraTest : StringSpec({
    "fromMidi" {
        fromMidi(60) shouldBe "C4"
        fromMidi(61) shouldBe "Db4"
        fromMidi(61, sharps = true) shouldBe "C#4"
        fromMidi(66) shouldBe "Gb4"
        fromMidi(66, sharps = true) shouldBe "F#4"
        fromMidi(21) shouldBe "A0"
        fromMidi(108) shouldBe "C8"
        fromMidi(60, pitchClass = true) shouldBe "C"
        fromMidi(61, pitchClass = true) shouldBe "Db"
    }

    "enharmonic" {
        println("[DEBUG_LOG] enharmonic(\"C4\", \"B#3\") -> ${enharmonic("C4", "B#3")}")
        enharmonic("C4", "B#3") shouldBe "B#3"
        enharmonic("B#3", "C4") shouldBe "C4"
        enharmonic("F4", "E#4") shouldBe "E#4"
        enharmonic("E#4", "F4") shouldBe "F4"
        enharmonic("C#4", "Db4") shouldBe "Db4"
        enharmonic("Db4", "C#4") shouldBe "C#4"

        // Pitch classes (no octave)
        enharmonic("C", "B#") shouldBe "B#"
        enharmonic("B#", "C") shouldBe "C"

        // Same note name
        enharmonic("C4", "C") shouldBe "C4"

        // Invalid or non-matching
        enharmonic("C4", "D4") shouldBe ""
        enharmonic("C4", "G") shouldBe ""
    }

    "sortedUniqNames" {
        sortedUniqNames(listOf("c2", "d3", "c5", "c4")) shouldBe listOf("C", "D")
        sortedUniqNames(listOf("f", "a", "c", "e", "g", "b", "d")) shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        sortedUniqNames(listOf("c#", "db", "d")) shouldBe listOf("C#", "Db", "D")
    }

    "sortedNoteNames" {
        sortedNoteNames(listOf("c2", "c5", "c1", "c0", "c6", "c")) shouldBe listOf("C", "C0", "C1", "C2", "C5", "C6")
    }

    "sortedUniqNoteNames" {
        sortedUniqNoteNames(listOf("a", "b", "c2", "1p", "p2", "c2", "b", "c", "c3")) shouldBe listOf(
            "C",
            "A",
            "B",
            "C2",
            "C3"
        )
    }

    "simplify" {
        simplify("C##") shouldBe "D"
        simplify("C###") shouldBe "D#"
        simplify("B#4") shouldBe "C5"
        simplify("Cbb") shouldBe "Bb"
    }

    "transposeOctaves" {
        io.peekandpoke.klang.tones.distance.transposeOctaves("C4", 1) shouldBe "C5"
        io.peekandpoke.klang.tones.distance.transposeOctaves("C4", -1) shouldBe "C3"
        io.peekandpoke.klang.tones.distance.transposeOctaves("C", 1) shouldBe "C"
    }
})
