package io.peekandpoke.klang.tones.note

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.midi.Midi

class NoteExamplesTest : StringSpec({
    "Note properties" {
        Note.get("C4").name shouldBe "C4"
        Note.get("C4").midi shouldBe 60

        Note.get("fx4").name shouldBe "F##4"
        Note.get("Ab5").pc shouldBe "Ab"
        Note.get("Eb").acc shouldBe "b"
        Note.get("C4").oct shouldBe 4
        Note.get("A4").midi shouldBe 69
        Note.get("A4").freq shouldBe 440.0
        Note.get("D").chroma shouldBe 2

        listOf("C", "D", "E").map { Note.get(it).chroma } shouldBe listOf(0, 2, 4)
    }

    "Note.fromMidi" {
        Note.fromMidi(61) shouldBe "Db4"
        // In TonalJS Note.fromMidi(61.7) returns "D4" because it rounds 61.7 to 62.
        // Our fromMidi takes Int, so we use midiToNoteName for Double.
        Midi.midiToNoteName(61.7) shouldBe "D4"

        listOf(60, 61, 62).map { Note.fromMidi(it) } shouldBe listOf("C4", "Db4", "D4")

        Note.fromMidi(61, sharps = true) shouldBe "C#4"
    }

    "Note.fromFreq" {
        // Equivalent to Note.fromFreq(440)
        Midi.midiToNoteName(Midi.freqToMidi(440.0)) shouldBe "A4"

        listOf(440.0, 550.0, 660.0).map { Midi.midiToNoteName(Midi.freqToMidi(it)) } shouldBe listOf("A4", "Db5", "E5")

        listOf(440.0, 550.0, 660.0).map { Midi.midiToNoteName(Midi.freqToMidi(it), sharps = true) } shouldBe listOf(
            "A4",
            "C#5",
            "E5"
        )
    }

    "Note.transposeNote and distance" {
        Distance.transpose("d3", "3M") shouldBe "F#3"
        Distance.transpose("D", "3M") shouldBe "F#"

        listOf("C", "D", "E").map { Distance.transpose(it, "5P") } shouldBe listOf("G", "A", "B")
        listOf("1P", "3M", "5P").map { Distance.transpose("C", it) } shouldBe listOf("C", "E", "G")

        Distance.transposeFifths("G4", 3) shouldBe "E6"
        Distance.transposeFifths("G", 3) shouldBe "E"

        listOf(0, 1, 2, 3, 4, 5, 6).map { Distance.transposeFifths("F#", it) } shouldBe
                listOf("F#", "C#", "G#", "D#", "A#", "E#", "B#")
        listOf(0, -1, -2, -3, -4, -5, -6).map { Distance.transposeFifths("Bb", it) } shouldBe
                listOf("Bb", "Eb", "Ab", "Db", "Gb", "Cb", "Fb")

        Distance.distance("C", "D") shouldBe "2M"
        Distance.distance("C3", "E3") shouldBe "3M"
        Distance.distance("C3", "E4") shouldBe "10M"
    }

    "Note collections" {
        // Test that invalid note names are filtered out
        Note.names(listOf("fx", "bb", "nothing")) shouldBe listOf("F##", "Bb")
        Note.names() shouldBe listOf("C", "D", "E", "F", "G", "A", "B")

        // Test type-safe collection handling
        Note.names(listOf("C2", "C#3", "Db4")).map { Note.get(it).pc } shouldBe
                listOf("C", "C#", "Db")

        Note.sortedNames(listOf("c2", "c5", "c1", "c0", "c6", "c")) shouldBe
                listOf("C", "C0", "C1", "C2", "C5", "C6")

        Note.sortedNames(listOf("c", "F", "G", "a", "b", "h", "J")) shouldBe
                listOf("C", "F", "G", "A", "B")

        // descending sort
        listOf("c2", "c5", "c1", "c0", "c6", "c")
            .map { Note.get(it) }
            .filter { !it.empty }
            .sortedWith(Note.Descending)
            .map { it.name } shouldBe
                listOf("C6", "C5", "C2", "C1", "C0", "C")
    }

    "Enharmonics" {
        Note.simplify("C#") shouldBe "C#"
        Note.simplify("C##") shouldBe "D"
        Note.simplify("C###") shouldBe "D#"

        Note.enharmonic("C#") shouldBe "Db"
        Note.enharmonic("C##") shouldBe "D"
        Note.enharmonic("C###") shouldBe "Eb"
        Note.enharmonic("C##b") shouldBe ""

        Note.enharmonic("C") shouldBe "C"
        Note.enharmonic("C4") shouldBe "C4"

        Note.enharmonic("F2", "E#") shouldBe "E#2"
        Note.enharmonic("B2", "Cb") shouldBe "Cb3"
        Note.enharmonic("C2", "B#") shouldBe "B#1"

        Note.enharmonic("F2", "Eb") shouldBe ""
    }

    "pitch-note examples" {
        Note.get("c4").name shouldBe "C4"
        Note.get("c4").oct shouldBe 4

        val ab4 = Note.get("ab4")
        println("[DEBUG_LOG] Ab4 freq: ${ab4.freq}")
        ab4.name shouldBe "Ab4"
        ab4.pc shouldBe "Ab"
        ab4.letter shouldBe "A"
        ab4.acc shouldBe "b"
        ab4.step shouldBe 5
        ab4.alt shouldBe -1
        ab4.oct shouldBe 4
        ab4.chroma shouldBe 8
        ab4.midi shouldBe 68
        // 415.3046975799451
        ab4.freq!! shouldBe (415.3046975799451 plusOrMinus 1e-10)

        Note.get("hello").empty shouldBe true
        Note.get("hello").name shouldBe ""
    }

    "notation-scientific examples" {
        Note.tokenize("Abb4 major") shouldBe listOf("A", "bb", "4", "major")
        // Note: TonalJS returns ["", "A", "bb", "4", "major"] but our tokenizeNote returns [letter, acc, oct, rest]
    }
})
