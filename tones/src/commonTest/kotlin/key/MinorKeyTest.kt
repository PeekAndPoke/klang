package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.scale.Scale

class MinorKeyTest : StringSpec({
    "minor keySignature" {
        val tonics = "C D E F G A B".split(" ")
        tonics.joinToString(" ") { Key.minorKey(it).keySignature } shouldBe "bbb b # bbbb bb  ##"
    }

    "natural scale names" {
        val chordScales = Key.minorKey("C").natural.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C3 minor",
            "D3 locrian",
            "Eb3 major",
            "F3 dorian",
            "G3 phrygian",
            "Ab3 lydian",
            "Bb3 mixolydian"
        )
    }

    "harmonic scale names" {
        val chordScales = Key.minorKey("C").harmonic.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C3 harmonic minor",
            "D3 locrian 6",
            "Eb3 major augmented",
            "F3 lydian diminished",
            "G3 phrygian dominant",
            "Ab3 lydian #9",
            "B3 ultralocrian"
        )
    }

    "melodic scale names" {
        val chordScales = Key.minorKey("C").melodic.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C3 melodic minor",
            "D3 dorian b2",
            "Eb3 lydian augmented",
            "F3 lydian dominant",
            "G3 mixolydian b6",
            "A3 locrian #2",
            "B3 altered"
        )
    }

    "octaves are discarded" {
        Key.minorKey("C4").melodic.scale.joinToString(" ") shouldBe "C D Eb F G A B"
        Key.minorKey("C4").melodic.chords.joinToString(" ") shouldBe "Cm6 Dm7 Eb+maj7 F7 G7 Am7b5 Bm7b5"
    }

    "empty minor key" {
        val emptyMinor = Key.minorKey("nothing")
        emptyMinor.type shouldBe "minor"
        emptyMinor.tonic shouldBe ""
    }
})
