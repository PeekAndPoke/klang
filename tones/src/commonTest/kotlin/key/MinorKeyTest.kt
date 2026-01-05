package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.scale.Scale

class MinorKeyTest : StringSpec({
    "minor keySignature" {
        val tonics = "C D E F G A B".split(" ")
        tonics.map { Key.minorKey(it).keySignature }.joinToString(" ") shouldBe "bbb b # bbbb bb  ##"
    }

    "natural scale names" {
        val chordScales = Key.minorKey("C").natural.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C minor",
            "D locrian",
            "Eb major",
            "F dorian",
            "G phrygian",
            "Ab lydian",
            "Bb mixolydian"
        )
    }

    "harmonic scale names" {
        val chordScales = Key.minorKey("C").harmonic.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C harmonic minor",
            "D locrian 6",
            "Eb major augmented",
            "F lydian diminished",
            "G phrygian dominant",
            "Ab lydian #9",
            "B ultralocrian"
        )
    }

    "melodic scale names" {
        val chordScales = Key.minorKey("C").melodic.chordScales
        chordScales.map { Scale.get(it).name } shouldBe listOf(
            "C melodic minor",
            "D dorian b2",
            "Eb lydian augmented",
            "F lydian dominant",
            "G mixolydian b6",
            "A locrian #2",
            "B altered"
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
