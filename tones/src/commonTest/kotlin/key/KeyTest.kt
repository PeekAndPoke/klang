package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.scale.Scale

class KeyTest : StringSpec({
    "majorTonicFromKeySignature" {
        Key.majorTonicFromKeySignature("###") shouldBe "A"
        Key.majorTonicFromKeySignature(3) shouldBe "A"
        Key.majorTonicFromKeySignature("b") shouldBe "F"
        Key.majorTonicFromKeySignature("bb") shouldBe "Bb"
        Key.majorTonicFromKeySignature("other") shouldBe null
    }

    "major keySignature" {
        val tonics = "C D E F G A B".split(" ")
        tonics.map { Key.majorKey(it).keySignature }.joinToString(" ") shouldBe " ## #### b # ### #####"
    }

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

    "secondary dominants" {
        Key.majorKey("C").secondaryDominants shouldBe listOf(
            "",
            "A7",
            "B7",
            "C7",
            "D7",
            "E7",
            ""
        )
    }

    "octaves are discarded" {
        Key.majorKey("b4").scale.joinToString(" ") shouldBe "B C# D# E F# G# A#"
        Key.majorKey("g4").chords.joinToString(" ") shouldBe "Gmaj7 Am7 Bm7 Cmaj7 D7 Em7 F#m7b5"
        Key.minorKey("C4").melodic.scale.joinToString(" ") shouldBe "C D Eb F G A B"
        Key.minorKey("C4").melodic.chords.joinToString(" ") shouldBe "Cm6 Dm7 Eb+maj7 F7 G7 Am7b5 Bm7b5"
    }

    "valid chord names" {
        val major = Key.majorKey("C")
        val minor = Key.minorKey("C")

        listOf(
            major.chords,
            major.secondaryDominants,
            major.substituteDominantSupertonics,
            major.substituteDominants,
            major.substituteDominantsMinorRelative,
            minor.natural.chords,
            minor.harmonic.chords,
            minor.melodic.chords
        ).forEach { chords ->
            chords.forEach { name ->
                if (name.isNotEmpty()) {
                    Chord.get(name).empty shouldBe false
                }
            }
        }
    }

    "C major chords" {
        val chords = Key.majorKeyChords("C")
        chords.find { it.name == "Em7" } shouldBe KeyChord(
            name = "Em7",
            roles = listOf("T", "ii/II")
        )
    }

    "empty major key" {
        val emptyMajor = Key.majorKey("")
        emptyMajor.type shouldBe "major"
        emptyMajor.tonic shouldBe ""
    }

    "empty minor key" {
        val emptyMinor = Key.minorKey("nothing")
        emptyMinor.type shouldBe "minor"
        emptyMinor.tonic shouldBe ""
    }
})
