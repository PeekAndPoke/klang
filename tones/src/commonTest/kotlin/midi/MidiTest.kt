package io.peekandpoke.klang.tones.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MidiTest : StringSpec({
    "isMidi" {
        Midi.isMidi(100) shouldBe true
        Midi.isMidi("100") shouldBe true
        Midi.isMidi(0) shouldBe true
        Midi.isMidi(127) shouldBe true
        Midi.isMidi(-1) shouldBe false
        Midi.isMidi(128) shouldBe false
        Midi.isMidi("blah") shouldBe false
    }

    "toMidi" {
        Midi.toMidi(100) shouldBe 100
        Midi.toMidi("C4") shouldBe 60
        Midi.toMidi("60") shouldBe 60
        Midi.toMidi(0) shouldBe 0
        Midi.toMidi("0") shouldBe 0
        Midi.toMidi(-1) shouldBe null
        Midi.toMidi(128) shouldBe null
        Midi.toMidi("blah") shouldBe null
        Midi.toMidi(Double.NaN) shouldBe null
    }

    "freqToMidi" {
        Midi.freqToMidi(220.0) shouldBe 57.0
        Midi.freqToMidi(261.62) shouldBe 60.0
        Midi.freqToMidi(261.0) shouldBe 59.96
    }

    "midiToFreq" {
        Midi.midiToFreq(60.0) shouldBe 261.6255653005986
        Midi.midiToFreq(69.0, 443.0) shouldBe 443.0
    }

    "midiToNoteName" {
        val notes = listOf(60.0, 61.0, 62.0, 63.0, 64.0, 65.0, 66.0, 67.0, 68.0, 69.0, 70.0, 71.0, 72.0)

        notes.joinToString(" ") { Midi.midiToNoteName(it) } shouldBe
                "C4 Db4 D4 Eb4 E4 F4 Gb4 G4 Ab4 A4 Bb4 B4 C5"

        notes.joinToString(" ") { Midi.midiToNoteName(it, sharps = true) } shouldBe
                "C4 C#4 D4 D#4 E4 F4 F#4 G4 G#4 A4 A#4 B4 C5"

        notes.joinToString(" ") { Midi.midiToNoteName(it, pitchClass = true) } shouldBe
                "C Db D Eb E F Gb G Ab A Bb B C"

        Midi.midiToNoteName(Double.NaN) shouldBe ""
        Midi.midiToNoteName(Double.NEGATIVE_INFINITY) shouldBe ""
        Midi.midiToNoteName(Double.POSITIVE_INFINITY) shouldBe ""
    }

    "pcSet from chroma" {
        Midi.pcSet("100100100101") shouldBe listOf(0, 3, 6, 9, 11)
    }

    "pcSet from midi" {
        Midi.pcSet(listOf(62, 63, 60, 65, 70, 72)) shouldBe listOf(0, 2, 3, 5, 10)
    }

    "pcSetNearest find nearest upwards" {
        val nearest = Midi.pcSetNearest(listOf(0, 5, 7))
        listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0).map { nearest(it) } shouldBe listOf(
            0.0, 0.0, 0.0, 5.0, 5.0, 5.0, 7.0, 7.0, 7.0, 7.0, 12.0, 12.0, 12.0
        )
    }

    "pcSetNearest chromatic to nearest C minor pentatonic" {
        val nearest = Midi.pcSetNearest("100101010010")
        listOf(
            36.0,
            37.0,
            38.0,
            39.0,
            40.0,
            41.0,
            42.0,
            43.0,
            44.0,
            45.0,
            46.0,
            47.0
        ).map { nearest(it) } shouldBe listOf(
            36.0, 36.0, 39.0, 39.0, 41.0, 41.0, 43.0, 43.0, 43.0, 46.0, 46.0, 48.0
        )
    }

    "pcSetNearest chromatic to nearest half octave" {
        val nearest = Midi.pcSetNearest("100000100000")
        listOf(
            36.0,
            37.0,
            38.0,
            39.0,
            40.0,
            41.0,
            42.0,
            43.0,
            44.0,
            45.0,
            46.0,
            47.0
        ).map { nearest(it) } shouldBe listOf(
            36.0, 36.0, 36.0, 42.0, 42.0, 42.0, 42.0, 42.0, 42.0, 48.0, 48.0, 48.0
        )
    }

    "pcSetNearest empty pcsets returns null" {
        listOf(10.0, 30.0, 40.0).map { Midi.pcSetNearest(emptyList<Int>())(it) } shouldBe listOf(null, null, null)
    }

    "pcSetSteps" {
        val scale = Midi.pcSetSteps("101010", 60.0)
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).map { scale(it) } shouldBe listOf(
            60.0, 62.0, 64.0, 72.0, 74.0, 76.0, 84.0, 86.0, 88.0, 96.0
        )
        listOf(0, -1, -2, -3, -4, -5, -6, -7, -8, -9).map { scale(it) } shouldBe listOf(
            60.0, 52.0, 50.0, 48.0, 40.0, 38.0, 36.0, 28.0, 26.0, 24.0
        )
    }

    "pcSetDegrees" {
        val scale = Midi.pcSetDegrees("101010", 60.0)
        listOf(1, 2, 3, 4, 5).map { scale(it) } shouldBe listOf(60.0, 62.0, 64.0, 72.0, 74.0)
        listOf(-1, -2, -3, 4, 5).map { scale(it) } shouldBe listOf(52.0, 50.0, 48.0, 72.0, 74.0)
        scale(0) shouldBe null
    }
})
