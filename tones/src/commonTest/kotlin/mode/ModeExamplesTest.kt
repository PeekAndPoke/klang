package io.peekandpoke.klang.tones.mode

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.distance.Distance

class ModeExamplesTest : StringSpec({
    "Mode.get" {
        val m = Mode.get("major")
        m.name shouldBe "ionian"
        m.aliases shouldBe listOf("major")
        m.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        m.modeNum shouldBe 0
        // m.mode shouldBe 2773
        m.alt shouldBe 0
        m.triad shouldBe ""
        m.seventh shouldBe "Maj7"
    }

    "Mode.names" {
        Mode.names() shouldBe listOf("ionian", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian")
    }

    "Mode.notes" {
        Mode.notes("ionian", "C") shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        Mode.notes("major", "C") shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        Mode.notes("minor", "C") shouldBe listOf("C", "D", "Eb", "F", "G", "Ab", "Bb")
    }

    "Mode.triads" {
        Mode.triads("major", "C") shouldBe listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim")
    }

    "Mode.seventhChords" {
        // Note: TonalJS README says "CMaj7" but it depends on chord alias preferences.
        // Also, it says B7b5 for the 7th degree of major? No, it should be Bm7b5.
        // Wait, major key 7th degree is half-diminished.
        Mode.seventhChords("major", "C") shouldBe listOf("CMaj7", "Dm7", "Em7", "FMaj7", "G7", "Am7", "Bm7b5")
    }

    "Mode.relativeTonic" {
        Mode.relativeTonic("aeolian", "ionian", "C") shouldBe "A"
        Mode.relativeTonic("minor", "major", "C") shouldBe "A"
    }

    "Get notes from a mode" {
        Mode.get("major").intervals.map { Distance.transpose("A", it) } shouldBe
                listOf("A", "B", "C#", "D", "E", "F#", "G#")
    }
})
