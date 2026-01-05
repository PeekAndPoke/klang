package io.peekandpoke.klang.tones.mode

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.pitch.NamedPitch

class ModeTest : StringSpec({
    "mode properties" {
        val ionian = Mode.get("ionian")
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

        Mode.get("major") shouldBe ionian
    }

    "accept NamedPitch as parameter" {
        Mode.get(Mode.get("major")) shouldBe Mode.get("major")
        Mode.get(object : NamedPitch {
            override val name = "Major"
        }) shouldBe Mode.get("major")
    }

    "name is case independent" {
        Mode.get("Dorian") shouldBe Mode.get("dorian")
    }

    "setNum" {
        val pcsets = Mode.names().map { Mode.get(it).setNum }
        pcsets shouldBe listOf(2773, 2902, 3418, 2741, 2774, 2906, 3434)
    }

    "alt" {
        val alt = Mode.names().map { Mode.get(it).alt }
        alt shouldBe listOf(0, 2, 4, -1, 1, 3, 5)
    }

    "triad" {
        val triads = Mode.names().map { Mode.get(it).triad }
        triads shouldBe listOf("", "m", "m", "", "", "m", "dim")
    }

    "seventh" {
        val sevenths = Mode.names().map { Mode.get(it).seventh }
        sevenths shouldBe listOf("Maj7", "m7", "m7", "Maj7", "7", "m7", "m7b5")
    }

    "aliases" {
        Mode.get("major") shouldBe Mode.get("ionian")
        Mode.get("minor") shouldBe Mode.get("aeolian")
    }

    "names" {
        Mode.names() shouldBe listOf(
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
        Mode.notes("major", "C").joinToString(" ") shouldBe "C D E F G A B"
        Mode.notes("dorian", "C").joinToString(" ") shouldBe "C D Eb F G A Bb"
        Mode.notes("dorian", "F").joinToString(" ") shouldBe "F G Ab Bb C D Eb"
        Mode.notes("lydian", "F").joinToString(" ") shouldBe "F G A B C D E"
        Mode.notes("anything", "F").joinToString(" ") shouldBe ""
    }

    "triads" {
        Mode.triads("minor", "C").joinToString(" ") shouldBe "Cm Ddim Eb Fm Gm Ab Bb"
        Mode.triads("mixolydian", "Bb").joinToString(" ") shouldBe "Bb Cm Ddim Eb Fm Gm Ab"
    }

    "seventhChords" {
        Mode.seventhChords("major", "C#").joinToString(" ") shouldBe "C#Maj7 D#m7 E#m7 F#Maj7 G#7 A#m7 B#m7b5"
        Mode.seventhChords("dorian", "G").joinToString(" ") shouldBe "Gm7 Am7 BbMaj7 C7 Dm7 Em7b5 FMaj7"
    }

    "relativeTonic" {
        Mode.relativeTonic("major", "minor", "A") shouldBe "C"
        Mode.relativeTonic("major", "minor", "D") shouldBe "F"
        Mode.relativeTonic("minor", "dorian", "D") shouldBe "A"
        Mode.relativeTonic("nonsense", "dorian", "D") shouldBe ""
    }
})
