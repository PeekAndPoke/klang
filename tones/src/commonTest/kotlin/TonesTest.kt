package io.peekandpoke.klang.tones

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesTest : StringSpec({
    "noteNameToMidi" {
        Tones.noteNameToMidi("C4") shouldBe 60.0
        Tones.noteNameToMidi("A4") shouldBe 69.0
        Tones.noteNameToMidi("C-1") shouldBe 0.0
        Tones.noteNameToMidi("60") shouldBe 60.0
        Tones.noteNameToMidi("") shouldBe 69.0
    }

    "noteToFreq - specific values" {
        Tones.noteToFreq("d5") shouldBe Tones.midiToFreq(74.0)
        Tones.noteToFreq("f5") shouldBe Tones.midiToFreq(77.0)
        Tones.noteToFreq("a5") shouldBe Tones.midiToFreq(81.0)
        Tones.noteToFreq("g5") shouldBe Tones.midiToFreq(79.0)
    }

    "noteNameToMidi for high notes" {
        // Tetris melody notes
        Tones.noteNameToMidi("d5") shouldBe 74.0
        Tones.noteNameToMidi("f5") shouldBe 77.0
        Tones.noteNameToMidi("a5") shouldBe 81.0
        Tones.noteNameToMidi("g5") shouldBe 79.0
    }

    "default octave is 3" {
        Tones.noteNameToMidi("C") shouldBe 48.0 // C3
        Tones.noteNameToMidi("A") shouldBe 57.0 // A3
    }

    "accidental aliases s and f" {
        Tones.noteNameToMidi("Cs4") shouldBe 61.0 // C#4
        Tones.noteNameToMidi("Cf4") shouldBe 59.0 // Cb4
    }

    "resolveFreq - absolute notes" {
        // High midi values (> 30) return absolute frequency
        Tones.resolveFreq("C4", "D minor") shouldBe Tones.noteToFreq("C4")
        Tones.resolveFreq("60", "D minor") shouldBe Tones.midiToFreq(60.0)
    }

    "resolveFreq - scale degree 0" {
        // Degree 0 of C3 major -> C3 (Midi 48)
        Tones.resolveFreq("0", "C3 major") shouldBe Tones.noteToFreq("C3")
        // Degree 0 of G4 minor -> G4 (Midi 67)
        Tones.resolveFreq("0", "G4 minor") shouldBe Tones.noteToFreq("G4")
    }

    "resolveFreq - scale degree steps" {
        // Degree 1 of C3 major -> D3 (48 + 2 = 50)
        Tones.resolveFreq("1", "C3 major") shouldBe Tones.noteToFreq("D3")

        // Degree 2 of C3 minor -> Eb3 (48 + 3 = 51)
        Tones.resolveFreq("2", "C3 minor") shouldBe Tones.noteToFreq("Eb3")

        // Degree 7 of C3 major -> C4 (48 + 12 = 60)
        Tones.resolveFreq("7", "C3 major") shouldBe Tones.noteToFreq("C4")
    }

    "resolveFreq - context parsing" {
        // Original Tones.kt handles colon split
        Tones.resolveFreq("0", "C3:major") shouldBe Tones.noteToFreq("C3")

        // In the original Tones.kt, " minor" splits to ["", "minor"]
        // noteNameToMidi("") returns 69.0 (A4), so the root becomes A4.
        Tones.resolveFreq("0", " minor") shouldBe 440.0

        // However, "minor" (no leading space) splits to ["minor"]
        // rootNote becomes "minor", noteNameToMidi("minor") fails regex and returns 69.0
        Tones.resolveFreq("0", "minor") shouldBe 440.0
    }

    "resolveFreq - negative degrees" {
        // Degree -1 of C3 major -> B2 (C3 - 1 semitone = 47)
        Tones.resolveFreq("-1", "C3 major") shouldBe Tones.noteToFreq("B2")
    }

    "resolveFreq - absolute notes in scale" {
        // These notes should be absolute because MIDI > 30
        Tones.resolveFreq("d5", "C minor") shouldBe Tones.noteToFreq("d5")
        Tones.resolveFreq("f5", "C minor") shouldBe Tones.noteToFreq("f5")
        Tones.resolveFreq("a5", "C minor") shouldBe Tones.noteToFreq("a5")
        Tones.resolveFreq("g5", "C minor") shouldBe Tones.noteToFreq("g5")
    }

    "resolveFreq for high notes" {
        // Should ignore scale and return absolute frequency
        Tones.resolveFreq("d5", "A3 minor") shouldBe Tones.midiToFreq(74.0)
        Tones.resolveFreq("a5", "A3 minor") shouldBe Tones.midiToFreq(81.0)
    }
})
