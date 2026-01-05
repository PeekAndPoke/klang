package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ScaleTest : StringSpec({
    "get" {
        val major = Scale.get("major")
        major.empty shouldBe false
        major.tonic shouldBe null
        major.notes shouldBe emptyList()
        major.type shouldBe "major"
        major.name shouldBe "major"
        major.intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        major.aliases shouldBe listOf("ionian")
        major.setNum shouldBe 2773
        major.chroma shouldBe "101011010101"
        major.normalized shouldBe "101010110101"

        val c5Pentatonic = Scale.get("c5 pentatonic")
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

        Scale.get("C4 Major") shouldBe Scale.get("C4 major")
    }

    "tokenize" {
        Scale.tokenize("c major") shouldBe Pair("C", "major")
        Scale.tokenize("cb3 major") shouldBe Pair("Cb3", "major")
        Scale.tokenize("melodic minor") shouldBe Pair("", "melodic minor")
        Scale.tokenize("dorian") shouldBe Pair("", "dorian")
        Scale.tokenize("c") shouldBe Pair("C", "")
        Scale.tokenize("") shouldBe Pair("", "")
    }

    "isKnown" {
        Scale.get("major").empty shouldBe false
        Scale.get("Db major").empty shouldBe false
        Scale.get("hello").empty shouldBe true
        Scale.get("").empty shouldBe true
        Scale.get("Maj7").empty shouldBe true
    }

    "getScale with mixed cases" {
        Scale.get("C lydian #5P PENTATONIC") shouldBe Scale.get("C lydian #5P pentatonic")
        Scale.get("lydian #5P PENTATONIC") shouldBe Scale.get("lydian #5P pentatonic")
    }

    "intervals" {
        Scale.get("major").intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        Scale.get("C major").intervals shouldBe listOf("1P", "2M", "3M", "4P", "5P", "6M", "7M")
        Scale.get("blah").intervals shouldBe emptyList()
    }

    "notes" {
        Scale.get("C major").notes shouldBe listOf("C", "D", "E", "F", "G", "A", "B")
        Scale.get("C lydian #9").notes shouldBe listOf("C", "D#", "E", "F#", "G", "A", "B")
        Scale.get("eb bebop").notes shouldBe listOf("Eb", "F", "G", "Ab", "Bb", "C", "Db", "D")
        Scale.get("C no-scale").notes shouldBe emptyList()
        Scale.get("no-note major").notes shouldBe emptyList()
    }

    "detectScale" {
        Scale.detect(listOf("D", "E", "F#", "A", "B"), matchExact = true) shouldBe listOf("D major pentatonic")
        Scale.detect(
            listOf("D", "E", "F#", "A", "B"),
            tonic = "B",
            matchExact = true
        ) shouldBe listOf("B minor pentatonic")
        Scale.detect(listOf("D", "F#", "B", "C", "C#"), matchExact = true) shouldBe emptyList()
        Scale.detect(listOf("c", "d", "e", "f", "g", "a", "b"), matchExact = true) shouldBe listOf("C major")
        Scale.detect(
            listOf("c2", "d6", "e3", "f1", "g7", "a6", "b5"),
            tonic = "d",
            matchExact = true
        ) shouldBe listOf("D dorian")

        Scale.detect(listOf("C", "D", "E", "F", "G", "A", "B"), matchExact = false) shouldBe listOf(
            "C major",
            "C bebop",
            "C bebop major",
            "C ichikosucho",
            "C chromatic",
        )
        Scale.detect(listOf("D", "F#", "B", "C", "C#"), matchExact = false) shouldBe listOf(
            "D bebop",
            "D kafi raga",
            "D chromatic"
        )
        Scale.detect(listOf("Ab", "Bb", "C", "Db", "Eb", "G")) shouldBe listOf(
            "Ab major",
            "Ab bebop",
            "Ab harmonic major",
            "Ab bebop major",
            "Ab ichikosucho",
            "Ab chromatic",
        )
    }

    "Ukrainian Dorian scale" {
        Scale.get("C romanian minor").notes shouldBe listOf("C", "D", "Eb", "F#", "G", "A", "Bb")
        Scale.get("C ukrainian dorian").notes shouldBe listOf("C", "D", "Eb", "F#", "G", "A", "Bb")
        Scale.get("B romanian minor").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
        Scale.get("B dorian #4").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
        Scale.get("B altered dorian").notes shouldBe listOf("B", "C#", "D", "E#", "F#", "G#", "A")
    }

    "extendedScales" {
        Scale.extended("major") shouldBe listOf(
            "bebop",
            "bebop major",
            "ichikosucho",
            "chromatic",
        )
        Scale.extended("none") shouldBe emptyList()
    }

    "reducedScales" {
        Scale.reduced("major") shouldBe listOf(
            "major pentatonic",
            "ionian pentatonic",
            "ritusen",
        )
        Scale.reduced("D major") shouldBe Scale.reduced("major")
        Scale.reduced("none") shouldBe emptyList()
    }

    "specific and problematic scales" {
        Scale.get("C whole tone").notes.joinToString(" ") shouldBe "C D E F# G# A#"
        Scale.get("Db whole tone").notes.joinToString(" ") shouldBe "Db Eb F G A B"
    }

    "scaleNotes" {
        Scale.notes(listOf("C4", "c3", "C5", "C4", "c4")) shouldBe listOf("C")
        Scale.notes(listOf("C4", "f3", "c#10", "b5", "d4", "cb4")) shouldBe listOf("C", "C#", "D", "F", "B", "Cb")
        Scale.notes(listOf("D4", "c#5", "A5", "F#6")) shouldBe listOf("D", "F#", "A", "C#")
    }

    "mode names" {
        Scale.modeNames("pentatonic") shouldBe listOf(
            Pair("1P", "major pentatonic"),
            Pair("2M", "egyptian"),
            Pair("3M", "malkos raga"),
            Pair("5P", "ritusen"),
            Pair("6M", "minor pentatonic"),
        )
        Scale.modeNames("whole tone pentatonic") shouldBe listOf(
            Pair("1P", "whole tone pentatonic"),
        )
        Scale.modeNames("C pentatonic") shouldBe listOf(
            Pair("C", "major pentatonic"),
            Pair("D", "egyptian"),
            Pair("E", "malkos raga"),
            Pair("G", "ritusen"),
            Pair("A", "minor pentatonic"),
        )
        Scale.modeNames("C whole tone pentatonic") shouldBe listOf(
            Pair("C", "whole tone pentatonic"),
        )
    }

    "rangeOfScale" {
        val range = Scale.rangeOfScale("C pentatonic")
        val result = range("C4", "C5")
        result.joinToString(" ") shouldBe "C4 D4 E4 G4 A4 C5"
        range("C5", "C4").joinToString(" ") shouldBe "C5 A4 G4 E4 D4 C4"
        range("g3", "a2").joinToString(" ") shouldBe "G3 E3 D3 C3 A2"

        val rangeFlat = Scale.rangeOfScale("Cb major")
        rangeFlat("Cb4", "Cb5").joinToString(" ") shouldBe "Cb4 Db4 Eb4 Fb4 Gb4 Ab4 Bb4 Cb5"

        val rangeSharp = Scale.rangeOfScale("C# major")
        rangeSharp("C#4", "C#5").joinToString(" ") shouldBe "C#4 D#4 E#4 F#4 G#4 A#4 B#4 C#5"

        val rangeNoTonic = Scale.rangeOfScale("pentatonic")
        rangeNoTonic("C4", "C5") shouldBe emptyList()

        val rangeNotes = Scale.rangeOfScale(listOf("c4", "g4", "db3", "g"))
        rangeNotes("c4", "c5").joinToString(" ") shouldBe "C4 Db4 G4 C5"
    }

    "scaleDegrees" {
        val degreesMajor = Scale.degrees("C major")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).joinToString(" ") { degreesMajor(it) } shouldBe
                "C D E F G A B C D E"

        val degreesC4Major = Scale.degrees("C4 major")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).joinToString(" ") { degreesC4Major(it) } shouldBe
                "C4 D4 E4 F4 G4 A4 B4 C5 D5 E5"

        val degreesC4Pentatonic = Scale.degrees("C4 pentatonic")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).joinToString(" ") { degreesC4Pentatonic(it) } shouldBe
                "C4 D4 E4 G4 A4 C5 D5 E5 G5 A5 C6"

        degreesMajor(0) shouldBe ""

        val degreesNegativeMajor = Scale.degrees("C major")
        listOf(-1, -2, -3, -4, -5, -6, -7, -8, -9, -10).joinToString(" ") { degreesNegativeMajor(it) } shouldBe
                "B A G F E D C B A G"
    }

    "scaleSteps" {
        listOf(-3, -2, -1, 0, 1, 2).map { Scale.steps("C4 major")(it) } shouldBe
                listOf("G3", "A3", "B3", "C4", "D4", "E4")
    }
})
