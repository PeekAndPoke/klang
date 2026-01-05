package io.peekandpoke.klang.tones.note

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pitch.Pitch
import io.peekandpoke.klang.tones.pitch.PitchCoordinates

class NoteTest : StringSpec({
    "tokenize" {
        tokenizeNote("Cbb5 major") shouldBe listOf("C", "bb", "5", "major")
        tokenizeNote("Ax") shouldBe listOf("A", "##", "", "")
        tokenizeNote("CM") shouldBe listOf("C", "", "", "M")
        tokenizeNote("maj7") shouldBe listOf("", "", "", "maj7")
        tokenizeNote("") shouldBe listOf("", "", "", "")
        tokenizeNote("bb") shouldBe listOf("B", "b", "", "")
        tokenizeNote("##") shouldBe listOf("", "##", "", "")
        tokenizeNote(" |\n") shouldBe listOf("", "", "", "")
    }

    "note properties from string" {
        val a4 = note("A4")
        a4.empty shouldBe false
        a4.name shouldBe "A4"
        a4.letter shouldBe "A"
        a4.acc shouldBe ""
        a4.pc shouldBe "A"
        a4.step shouldBe 5
        a4.alt shouldBe 0
        a4.oct shouldBe 4
        a4.coord shouldBe PitchCoordinates.Note(3, 3)
        a4.height shouldBe 69
        a4.chroma shouldBe 9
        a4.midi shouldBe 69
        a4.freq shouldBe 440.0
    }

    "it accepts a Note as param" {
        note(note("C4")) shouldBe note("C4")
    }

    "height" {
        fun height(str: String) = str.split(" ").map { note(it).height }
        height("C4 D4 E4 F4 G4") shouldBe listOf(60, 62, 64, 65, 67)
        height("B-2 C-1 D-1") shouldBe listOf(-1, 0, 2)
        height("F9 G9 A9") shouldBe listOf(125, 127, 129)
        height("C-4 D-4 E-4 F-4 G-4") shouldBe listOf(-36, -34, -32, -31, -29)
        height("C D E F G") shouldBe listOf(-1188, -1186, -1184, -1183, -1181)

        height("Cb4 Cbb4 Cbbb4 B#4 B##4 B###4") shouldBe height("B3 Bb3 Bbb3 C5 C#5 C##5")
        height("Cb Cbb Cbbb B# B## B###") shouldBe height("B Bb Bbb C C# C##")
    }

    "midi" {
        fun midi(str: String) = str.split(" ").map { note(it).midi }
        midi("C4 D4 E4 F4 G4") shouldBe listOf(60, 62, 64, 65, 67)
        midi("B-2 C-1 D-1") shouldBe listOf(null, 0, 2)
        midi("F9 G9 A9") shouldBe listOf(125, 127, null)
        midi("C-4 D-4 E-4 F-4") shouldBe listOf(null, null, null, null)
        midi("C D E F") shouldBe listOf(null, null, null, null)
    }

    "freq" {
        note("C4").freq shouldBe 261.6255653005986
        note("B-2").freq shouldBe 7.716926582126941
        note("F9").freq shouldBe 11175.303405856126
        note("C-4").freq shouldBe 1.0219748644554634
        note("C").freq shouldBe null
        note("x").freq shouldBe null
    }

    "note properties from pitch properties" {
        note(object : io.peekandpoke.klang.tones.pitch.Pitch {
            override val step = 1
            override val alt = -1
            override val oct = null
            override val dir = null
        }).name shouldBe "Db"

        note(Pitch(step = 2, alt = 1)).name shouldBe "E#"
        note(Pitch(step = 2, alt = 1, oct = 4)).name shouldBe "E#4"
        note(Pitch(step = 5, alt = 0)).name shouldBe "A"

        // Handling invalid step values (like step = -1 or step = 8 in TS tests)
        // In our Pitch factory, we don't have validation yet, but note() calls pitchName()
        // which uses getOrNull on "CDEFGAB"
        note(Pitch(step = -1, alt = 0)).name shouldBe ""
        note(Pitch(step = 8, alt = 0)).name shouldBe ""
    }
})
