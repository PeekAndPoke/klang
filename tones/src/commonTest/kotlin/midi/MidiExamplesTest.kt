package io.peekandpoke.klang.tones.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MidiExamplesTest : StringSpec({
    "Midi.toMidi" {
        toMidi("C4") shouldBe 60
        toMidi("#") shouldBe null
        toMidi(60) shouldBe 60
        toMidi("60") shouldBe 60
        toMidi(-1) shouldBe null
    }

    "Midi.midiToFreq" {
        midiToFreq(60.0) shouldBe (261.6255653005986 plusOrMinus 1e-10)
        midiToFreq(69.0) shouldBe 440.0
        midiToFreq(69.0, 443.0) shouldBe 443.0
    }

    "Midi.midiToNoteName" {
        midiToNoteName(61.0) shouldBe "Db4"
        midiToNoteName(61.0, pitchClass = true) shouldBe "Db"
        midiToNoteName(61.0, sharps = true) shouldBe "C#4"
        midiToNoteName(61.0, pitchClass = true, sharps = true) shouldBe "C#"
        midiToNoteName(61.7) shouldBe "D4"
    }

    "Midi.freqToMidi" {
        freqToMidi(220.0) shouldBe 57.0
        freqToMidi(261.6255653) shouldBe (60.0 plusOrMinus 1e-2)
        // Note: TonalJS says 59.96 for 261, let's see
        freqToMidi(261.0) shouldBe (59.96 plusOrMinus 1e-2)
    }

    "Midi.pcset" {
        pcset(listOf(62, 63, 60, 65, 70, 72)) shouldBe listOf(0, 2, 3, 5, 10)
        pcset("100100100101") shouldBe listOf(0, 3, 6, 9, 11)
    }

    "Midi.pcsetNearest" {
        val nearest = pcsetNearest("101011010101")
        val nearestFn = pcsetNearest("101011010101")
        listOf(60, 61, 62, 63, 64, 65, 66).map { nearestFn(it.toDouble()) } shouldBe
                listOf(60.0, 62.0, 62.0, 64.0, 64.0, 65.0, 67.0)
    }

    "Midi.pcsetSteps" {
        val steps = pcsetSteps("101011010101", 60.0)
        listOf(-2, -1, 0, 1, 2, 3).map { steps(it) } shouldBe
                listOf(57.0, 59.0, 60.0, 62.0, 64.0, 65.0)
    }
})
