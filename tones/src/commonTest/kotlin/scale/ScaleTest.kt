package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ScaleTest : StringSpec({
    "get" {
        val major = getScale("major")
        major.empty shouldBe false
        major.tonic shouldBe null
        major.notes shouldBe emptyList<String>()
        major.type shouldBe "major"
        major.name shouldBe "major"
        major.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        major.aliases shouldBe listOf("ionian")
        major.setNum shouldBe 2773
        major.chroma shouldBe "101011010101"
        major.normalized shouldBe "101010110101"

        val c5Pentatonic = getScale("c5 pentatonic")
        c5Pentatonic.empty shouldBe false
        c5Pentatonic.name shouldBe "C5 major pentatonic"
        c5Pentatonic.type shouldBe "major pentatonic"
        c5Pentatonic.tonic shouldBe "C5"
        c5Pentatonic.notes shouldBe listOf("C5", "D5", "E5", "G5", "A5")
        c5Pentatonic.intervals shouldBe listOf("1P", "2M", "3M", "5P", "6M")
        c5Pentatonic.aliases shouldBe listOf("pentatonic")
        c5Pentatonic.setNum shouldBe 2708
        c5Pentatonic.chroma shouldBe "101010010100"
        c5Pentatonic.normalized shouldBe "100101001010"

        getScale("C4 Major") shouldBe getScale("C4 major")
    }

    "tokenize" {
        tokenizeScale("c major") shouldBe Pair("C", "major")
        tokenizeScale("cb3 major") shouldBe Pair("Cb3", "major")
        tokenizeScale("melodic minor") shouldBe Pair("", "melodic minor")
        tokenizeScale("dorian") shouldBe Pair("", "dorian")
        tokenizeScale("c") shouldBe Pair("C", "")
        tokenizeScale("") shouldBe Pair("", "")
    }

    "isKnown" {
        getScale("major").empty shouldBe false
        getScale("Db major").empty shouldBe false
        getScale("hello").empty shouldBe true
        getScale("").empty shouldBe true
        getScale("Maj7").empty shouldBe true
    }

    "getScale with mixed cases" {
        getScale("C lydian #5P PENTATONIC") shouldBe getScale("C lydian #5P pentatonic")
        getScale("lydian #5P PENTATONIC") shouldBe getScale("lydian #5P pentatonic")
    }

    "intervals" {
        getScale("major").intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        getScale("C major").intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        getScale("blah").intervals shouldBe emptyList<String>()
    }

    "notes" {
        getScale("C major").notes shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        getScale("C lydian #9").notes shouldBe listOf("C", "D#", "E", "F#", "G", "A", "B")
        getScale("eb bebop").notes shouldBe listOf("Eb", "F", "G", "Ab", "Bb", "C", "Db", "D")
        getScale("C no-scale").notes shouldBe emptyList<String>()
        getScale("no-note major").notes shouldBe emptyList<String>()
    }

    "detectScale" {
        detectScale(listOf("D", "E", "F#", "A", "B"), matchExact = true) shouldBe listOf("D major pentatonic")
        detectScale(
            listOf("D", "E", "F#", "A", "B"),
            tonic = "B",
            matchExact = true
        ) shouldBe listOf("B minor pentatonic")
        detectScale(listOf("D", "F#", "B", "C", "C#"), matchExact = true) shouldBe emptyList<String>()
        detectScale(listOf("c", "d", "e", "f", "g", "a", "b"), matchExact = true) shouldBe listOf("C major")
        detectScale(
            listOf("c2", "d6", "e3", "f1", "g7", "a6", "b5"),
            tonic = "d",
            matchExact = true
        ) shouldBe listOf("D dorian")

        detectScale(listOf("C", "D", "E", "F", "G", "A", "B"), matchExact = false) shouldBe listOf(
            "C major",
            "C bebop",
            "C bebop major",
            "C ichikosucho",
            "C chromatic",
        )
        detectScale(listOf("D", "F#", "B", "C", "C#"), matchExact = false) shouldBe listOf(
            "D bebop",
            "D kafi raga",
            "D chromatic"
        )
        detectScale(listOf("Ab", "Bb", "C", "Db", "Eb", "G")) shouldBe listOf(
            "Ab major",
            "Ab bebop",
            "Ab harmonic major",
            "Ab bebop major",
            "Ab ichikosucho",
            "Ab chromatic",
        )
    }

    "Ukrainian Dorian scale" {
        getScale("C romanian minor").notes shouldBe listOf("C", "D", "Eb", "F#", "G", "A", "Bb")
        getScale("C ukrainian dorian").notes shouldBe listOf("C", "D", "Eb", "F#", "G", "A", "Bb")
        getScale("B romanian minor").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
        getScale("B dorian #4").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
        getScale("B altered dorian").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
    }

    "extendedScales" {
        extendedScales("major") shouldBe listOf(
            "bebop",
            "bebop major",
            "ichikosucho",
            "chromatic",
        )
        extendedScales("none") shouldBe emptyList<String>()
    }

    "reducedScales" {
        reducedScales("major") shouldBe listOf(
            "major pentatonic",
            "ionian pentatonic",
            "ritusen",
        )
        reducedScales("D major") shouldBe reducedScales("major")
        reducedScales("none") shouldBe emptyList<String>()
    }

    "specific and problematic scales" {
        getScale("C whole tone").notes.joinToString(" ") shouldBe "C D E F# G# A#"
        getScale("Db whole tone").notes.joinToString(" ") shouldBe "Db Eb F G A B"
    }

    "scaleNotes" {
        scaleNotes(listOf("C4", "c3", "C5", "C4", "c4")) shouldBe listOf("C")
        scaleNotes(listOf("C4", "f3", "c#10", "b5", "d4", "cb4")) shouldBe listOf("C", "C#", "D", "F", "B", "Cb")
        scaleNotes(listOf("D4", "c#5", "A5", "F#6")) shouldBe listOf("D", "F#", "A", "C#")
    }

    "mode names" {
        modeNames("pentatonic") shouldBe listOf(
            Pair("1P", "major pentatonic"),
            Pair("2M", "egyptian"),
            Pair("3M", "malkos raga"),
            Pair("5P", "ritusen"),
            Pair("6M", "minor pentatonic"),
        )
        modeNames("whole tone pentatonic") shouldBe listOf(
            Pair("1P", "whole tone pentatonic"),
        )
        modeNames("C pentatonic") shouldBe listOf(
            Pair("C", "major pentatonic"),
            Pair("D", "egyptian"),
            Pair("E", "malkos raga"),
            Pair("G", "ritusen"),
            Pair("A", "minor pentatonic"),
        )
        modeNames("C whole tone pentatonic") shouldBe listOf(
            Pair("C", "whole tone pentatonic"),
        )
    }

    "rangeOfScale" {
        val range = rangeOfScale("C pentatonic")
        val result = range("C4", "C5")
        result.joinToString(" ") shouldBe "C4 D4 E4 G4 A4 C5"
        range("C5", "C4").joinToString(" ") shouldBe "C5 A4 G4 E4 D4 C4"
        range("g3", "a2").joinToString(" ") shouldBe "G3 E3 D3 C3 A2"

        val rangeFlat = rangeOfScale("Cb major")
        rangeFlat("Cb4", "Cb5").joinToString(" ") shouldBe "Cb4 Db4 Eb4 Fb4 Gb4 Ab4 Bb4 Cb5"

        val rangeSharp = rangeOfScale("C# major")
        rangeSharp("C#4", "C#5").joinToString(" ") shouldBe "C#4 D#4 E#4 F#4 G#4 A#4 B#4 C#5"

        val rangeNoTonic = rangeOfScale("pentatonic")
        rangeNoTonic("C4", "C5") shouldBe emptyList<String>()

        val rangeNotes = rangeOfScale(listOf("c4", "g4", "db3", "g"))
        rangeNotes("c4", "c5").joinToString(" ") shouldBe "C4 Db4 G4 C5"
    }

    "scaleDegrees" {
        val degreesMajor = scaleDegrees("C major")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map { degreesMajor(it) }.joinToString(" ") shouldBe "C D E F G A B C D E"

        val degreesC4Major = scaleDegrees("C4 major")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map { degreesC4Major(it) }
            .joinToString(" ") shouldBe "C4 D4 E4 F4 G4 A4 B4 C5 D5 E5"

        val degreesC4Pentatonic = scaleDegrees("C4 pentatonic")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).map { degreesC4Pentatonic(it) }
            .joinToString(" ") shouldBe "C4 D4 E4 G4 A4 C5 D5 E5 G5 A5 C6"

        degreesMajor(0) shouldBe ""

        val degreesNegativeMajor = scaleDegrees("C major")
        listOf(-1, -2, -3, -4, -5, -6, -7, -8, -9, -10).map { degreesNegativeMajor(it) }
            .joinToString(" ") shouldBe "B A G F E D C B A G"
    }

    "scaleSteps" {
        listOf(-3, -2, -1, 0, 1, 2).map { scaleSteps("C4 major")(it) } shouldBe listOf(
            "G3",
            "A3",
            "B3",
            "C4",
            "D4",
            "E4"
        )
    }
})
