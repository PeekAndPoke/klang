package io.peekandpoke.klang.tones.mode

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pitch.NamedPitch

class ModeTest : StringSpec({
    "mode properties" {
        val ionian = getMode("ionian")
        ionian.empty shouldBe false
        ionian.modeNum shouldBe 0
        ionian.name shouldBe "ionian"
        ionian.setNum shouldBe 2773
        ionian.chroma shouldBe "101011010101"
        ionian.normalized shouldBe "101011010101"
        ionian.alt shouldBe 0
        ionian.triad shouldBe ""
        ionian.seventh shouldBe "Maj7"
        ionian.aliases shouldBe listOf("major")
        ionian.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")

        getMode("major") shouldBe ionian
    }

    "accept NamedPitch as parameter" {
        getMode(getMode("major")) shouldBe getMode("major")
        getMode(object : NamedPitch {
            override val name = "Major"
        }) shouldBe getMode("major")
    }

    "name is case independent" {
        getMode("Dorian") shouldBe getMode("dorian")
    }

    "setNum" {
        val pcsets = modeNames().map { getMode(it).setNum }
        pcsets shouldBe listOf(2773, 2902, 3418, 2741, 2774, 2906, 3434)
    }

    "alt" {
        val alt = modeNames().map { getMode(it).alt }
        alt shouldBe listOf(0, 2, 4, -1, 1, 3, 5)
    }

    "triad" {
        val triads = modeNames().map { getMode(it).triad }
        triads shouldBe listOf("", "m", "m", "", "", "m", "dim")
    }

    "seventh" {
        val sevenths = modeNames().map { getMode(it).seventh }
        sevenths shouldBe listOf("Maj7", "m7", "m7", "Maj7", "7", "m7", "m7b5")
    }

    "aliases" {
        getMode("major") shouldBe getMode("ionian")
        getMode("minor") shouldBe getMode("aeolian")
    }

    "names" {
        modeNames() shouldBe listOf(
            "ionian",
            "dorian",
            "phrygian",
            "lydian",
            "mixolydian",
            "aeolian",
            "locrian"
        )
    }

    "notes" {
        modeNotes("major", "C").joinToString(" ") shouldBe "C D E F G A B"
        modeNotes("dorian", "C").joinToString(" ") shouldBe "C D Eb F G A Bb"
        modeNotes("dorian", "F").joinToString(" ") shouldBe "F G Ab Bb C D Eb"
        modeNotes("lydian", "F").joinToString(" ") shouldBe "F G A B C D E"
        modeNotes("anything", "F").joinToString(" ") shouldBe ""
    }

    "triads" {
        modeTriads("minor", "C").joinToString(" ") shouldBe "Cm Ddim Eb Fm Gm Ab Bb"
        modeTriads("mixolydian", "Bb").joinToString(" ") shouldBe "Bb Cm Ddim Eb Fm Gm Ab"
    }

    "seventhChords" {
        modeSeventhChords("major", "C#").joinToString(" ") shouldBe "C#Maj7 D#m7 E#m7 F#Maj7 G#7 A#m7 B#m7b5"
        modeSeventhChords("dorian", "G").joinToString(" ") shouldBe "Gm7 Am7 BbMaj7 C7 Dm7 Em7b5 FMaj7"
    }

    "relativeTonic" {
        modeRelativeTonic("major", "minor", "A") shouldBe "C"
        modeRelativeTonic("major", "minor", "D") shouldBe "F"
        modeRelativeTonic("minor", "dorian", "D") shouldBe "A"
        modeRelativeTonic("nonsense", "dorian", "D") shouldBe ""
    }
})
