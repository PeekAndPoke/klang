package io.peekandpoke.klang.tones.mode

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ModeTest : StringSpec({
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
