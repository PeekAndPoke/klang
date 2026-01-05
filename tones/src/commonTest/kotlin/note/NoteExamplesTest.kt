package io.peekandpoke.klang.tones.note

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.distance.distance
import io.peekandpoke.klang.tones.distance.transpose
import io.peekandpoke.klang.tones.distance.transposeFifths
import io.peekandpoke.klang.tones.midi.freqToMidi
import io.peekandpoke.klang.tones.midi.midiToNoteName

class NoteExamplesTest : StringSpec({
    "Note properties" {
        note("C4").name shouldBe "C4"
        note("C4").midi shouldBe 60

        note("fx4").name shouldBe "F##4"
        note("Ab5").pc shouldBe "Ab"
        note("Eb").acc shouldBe "b"
        note("C4").oct shouldBe 4
        note("A4").midi shouldBe 69
        note("A4").freq shouldBe 440.0
        note("D").chroma shouldBe 2

        listOf("C", "D", "E").map { note(it).chroma } shouldBe listOf(0, 2, 4)
    }

    "Note.fromMidi" {
        fromMidi(61) shouldBe "Db4"
        // In TonalJS Note.fromMidi(61.7) returns "D4" because it rounds 61.7 to 62.
        // Our fromMidi takes Int, so we use midiToNoteName for Double.
        midiToNoteName(61.7) shouldBe "D4"

        listOf(60, 61, 62).map { fromMidi(it) } shouldBe listOf("C4", "Db4", "D4")

        fromMidi(61, sharps = true) shouldBe "C#4"
    }

    "Note.fromFreq" {
        // Equivalent to Note.fromFreq(440)
        midiToNoteName(freqToMidi(440.0)) shouldBe "A4"

        listOf(440.0, 550.0, 660.0).map { midiToNoteName(freqToMidi(it)) } shouldBe listOf("A4", "Db5", "E5")

        listOf(440.0, 550.0, 660.0).map { midiToNoteName(freqToMidi(it), sharps = true) } shouldBe listOf(
            "A4",
            "C#5",
            "E5"
        )
    }

    "Note.transpose and distance" {
        transpose("d3", "3M") shouldBe "F#3"
        transpose("D", "3M") shouldBe "F#"

        listOf("C", "D", "E").map { transpose(it, "5P") } shouldBe listOf("G", "A", "B")
        listOf("1P", "3M", "5P").map { transpose("C", it) } shouldBe listOf("C", "E", "G")

        transposeFifths("G4", 3) shouldBe "E6"
        transposeFifths("G", 3) shouldBe "E"

        listOf(0, 1, 2, 3, 4, 5, 6).map { transposeFifths("F#", it) } shouldBe
                listOf("F#", "C#", "G#", "D#", "A#", "E#", "B#")
        listOf(0, -1, -2, -3, -4, -5, -6).map { transposeFifths("Bb", it) } shouldBe
                listOf("Bb", "Eb", "Ab", "Db", "Gb", "Cb", "Fb")

        distance("C", "D") shouldBe "2M"
        distance("C3", "E3") shouldBe "3M"
        distance("C3", "E4") shouldBe "10M"
    }

    "Note collections" {
        noteNames(listOf("fx", "bb", 12, "nothing", {}, null)) shouldBe listOf("F##", "Bb")
        noteNames() shouldBe listOf("C", "D", "E", "F", "G", "A", "B")

        noteNames(listOf("C2", "C#3", "Db4", 12, "nothing", {}, null)).map { note(it).pc } shouldBe
                listOf("C", "C#", "Db")

        sortedNoteNames(listOf("c2", "c5", "c1", "c0", "c6", "c")) shouldBe
                listOf("C", "C0", "C1", "C2", "C5", "C6")

        sortedNoteNames(listOf("c", "F", "G", "a", "b", "h", "J")) shouldBe
                listOf("C", "F", "G", "A", "B")

        // descending sort
        listOf("c2", "c5", "c1", "c0", "c6", "c")
            .map { note(it) }
            .filter { !it.empty }
            .sortedWith(Descending)
            .map { it.name } shouldBe
                listOf("C6", "C5", "C2", "C1", "C0", "C")
    }

    "Enharmonics" {
        simplify("C#") shouldBe "C#"
        simplify("C##") shouldBe "D"
        simplify("C###") shouldBe "D#"

        enharmonic("C#") shouldBe "Db"
        enharmonic("C##") shouldBe "D"
        enharmonic("C###") shouldBe "Eb"
        enharmonic("C##b") shouldBe ""

        enharmonic("C") shouldBe "C"
        enharmonic("C4") shouldBe "C4"

        enharmonic("F2", "E#") shouldBe "E#2"
        enharmonic("B2", "Cb") shouldBe "Cb3"
        enharmonic("C2", "B#") shouldBe "B#1"

        enharmonic("F2", "Eb") shouldBe ""
    }

    "pitch-note examples" {
        note("c4").name shouldBe "C4"
        note("c4").oct shouldBe 4

        val ab4 = note("ab4")
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

        note("hello").empty shouldBe true
        note("hello").name shouldBe ""
    }

    "notation-scientific examples" {
        tokenizeNote("Abb4 major") shouldBe listOf("A", "bb", "4", "major")
        // Note: TonalJS returns ["", "A", "bb", "4", "major"] but our tokenizeNote returns [letter, acc, oct, rest]
    }
})
