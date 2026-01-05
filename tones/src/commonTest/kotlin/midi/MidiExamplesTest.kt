package io.peekandpoke.klang.tones.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MidiExamplesTest : StringSpec({
    "Midi.toMidi" {
        Midi.toMidi("C4") shouldBe 60
        Midi.toMidi("#") shouldBe null
        Midi.toMidi(60) shouldBe 60
        Midi.toMidi("60") shouldBe 60
        Midi.toMidi(-1) shouldBe null
    }

    "Midi.midiToFreq" {
        Midi.midiToFreq(60.0) shouldBe (261.6255653005986 plusOrMinus 1e-10)
        Midi.midiToFreq(69.0) shouldBe 440.0
        Midi.midiToFreq(69.0, 443.0) shouldBe 443.0
    }

    "Midi.midiToNoteName" {
        Midi.midiToNoteName(61.0) shouldBe "Db4"
        Midi.midiToNoteName(61.0, pitchClass = true) shouldBe "Db"
        Midi.midiToNoteName(61.0, sharps = true) shouldBe "C#4"
        Midi.midiToNoteName(61.0, pitchClass = true, sharps = true) shouldBe "C#"
        Midi.midiToNoteName(61.7) shouldBe "D4"
    }

    "Midi.freqToMidi" {
        Midi.freqToMidi(220.0) shouldBe 57.0
        Midi.freqToMidi(261.6255653) shouldBe (60.0 plusOrMinus 1e-2)
        // Note: TonalJS says 59.96 for 261, let's see
        Midi.freqToMidi(261.0) shouldBe (59.96 plusOrMinus 1e-2)
    }

    "Midi.pcSet" {
        Midi.pcSet(listOf(62, 63, 60, 65, 70, 72)) shouldBe listOf(0, 2, 3, 5, 10)
        Midi.pcSet("100100100101") shouldBe listOf(0, 3, 6, 9, 11)
    }

    "Midi.pcSetNearest" {
        val nearestFn = Midi.pcSetNearest("101011010101")

        listOf(60, 61, 62, 63, 64, 65, 66).map { nearestFn(it.toDouble()) } shouldBe
                listOf(60.0, 62.0, 62.0, 64.0, 64.0, 65.0, 67.0)
    }

    "Midi.pcSetSteps" {
        val steps = Midi.pcSetSteps("101011010101", 60.0)

        listOf(-2, -1, 0, 1, 2, 3).map { steps(it) } shouldBe
                listOf(57.0, 59.0, 60.0, 62.0, 64.0, 65.0)
    }
})
