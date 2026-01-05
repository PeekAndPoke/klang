package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyExamplesTest : StringSpec({
    "majorKey" {
        val cMajor = Key.majorKey("C")
        cMajor.tonic shouldBe "C"
        cMajor.type shouldBe "major"
        cMajor.minorRelative shouldBe "A"
        cMajor.alteration shouldBe 0
        cMajor.keySignature shouldBe ""
        cMajor.grades shouldBe listOf("I", "II", "III", "IV", "V", "VI", "VII")
        cMajor.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        cMajor.scale shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        cMajor.triads shouldBe listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim")
        cMajor.chords shouldBe listOf("Cmaj7", "Dm7", "Em7", "Fmaj7", "G7", "Am7", "Bm7b5")
        cMajor.chordsHarmonicFunction shouldBe listOf("T", "SD", "T", "SD", "D", "T", "D")
        cMajor.chordScales shouldBe listOf(
            "C major",
            "D dorian",
            "E phrygian",
            "F lydian",
            "G mixolydian",
            "A minor",
            "B locrian"
        )
        cMajor.secondaryDominants shouldBe listOf("", "A7", "B7", "C7", "D7", "E7", "")
        cMajor.secondaryDominantsMinorRelative shouldBe listOf("", "Em7", "F#m7", "Gm7b5", "Am7b5", "Bm7", "")
        cMajor.substituteDominants shouldBe listOf("", "Eb7", "F7", "Gb7", "Ab7", "Bb7", "")
        cMajor.substituteDominantsMinorRelative shouldBe listOf("", "Bbm7", "Cm7", "Dbm7b5", "Ebm7b5", "Fm7", "")
    }

    "minorKey" {
        val cMinor = Key.minorKey("C")
        cMinor.tonic shouldBe "C"
        cMinor.type shouldBe "minor"
        cMinor.relativeMajor shouldBe "Eb"
        cMinor.alteration shouldBe -3
        cMinor.keySignature shouldBe "bbb"

        cMinor.natural.scale shouldBe listOf("C", "D", "Eb", "F", "G", "Ab", "Bb")
        cMinor.natural.triads shouldBe listOf("Cm", "Ddim", "Eb", "Fm", "Gm", "Ab", "Bb")
        cMinor.natural.chords shouldBe listOf("Cm7", "Dm7b5", "Ebmaj7", "Fm7", "Gm7", "Abmaj7", "Bb7")

        cMinor.harmonic.scale shouldBe listOf("C", "D", "Eb", "F", "G", "Ab", "B")
        cMinor.harmonic.triads shouldBe listOf("Cm", "Ddim", "Ebaug", "Fm", "G", "Ab", "Bdim")
        cMinor.harmonic.chords shouldBe listOf("CmMaj7", "Dm7b5", "Eb+maj7", "Fm7", "G7", "Abmaj7", "Bo7")

        cMinor.melodic.scale shouldBe listOf("C", "D", "Eb", "F", "G", "A", "B")
        cMinor.melodic.triads shouldBe listOf("Cm", "Dm", "Ebaug", "F", "G", "Adim", "Bdim")
        cMinor.melodic.chords shouldBe listOf("Cm6", "Dm7", "Eb+maj7", "F7", "G7", "Am7b5", "Bm7b5")
    }

    "majorTonicFromKeySignature" {
        Key.majorTonicFromKeySignature("bbb") shouldBe "Eb"
        Key.majorTonicFromKeySignature("###") shouldBe "A"
        Key.majorKey(Key.majorTonicFromKeySignature("###")!!).minorRelative shouldBe "F#"
    }
})
