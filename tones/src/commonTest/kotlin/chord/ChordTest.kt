package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChordTest : StringSpec({
    "tokenize" {
        Chord.tokenize("Cmaj7") shouldBe Triple("C", "maj7", "")
        Chord.tokenize("c7") shouldBe Triple("C", "7", "")
        Chord.tokenize("maj7") shouldBe Triple("", "maj7", "")
        Chord.tokenize("c#4 m7b5") shouldBe Triple("C#", "4m7b5", "")
        Chord.tokenize("c#4m7b5") shouldBe Triple("C#", "4m7b5", "")
        Chord.tokenize("Cb7b5") shouldBe Triple("Cb", "7b5", "")
        Chord.tokenize("Eb7add6") shouldBe Triple("Eb", "7add6", "")
        Chord.tokenize("Bb6b5") shouldBe Triple("Bb", "6b5", "")
        Chord.tokenize("aug") shouldBe Triple("", "aug", "")
        Chord.tokenize("C11") shouldBe Triple("C", "11", "")
        Chord.tokenize("C13no5") shouldBe Triple("C", "13no5", "")
        Chord.tokenize("C64") shouldBe Triple("C", "64", "")
        Chord.tokenize("C9") shouldBe Triple("C", "9", "")
        Chord.tokenize("C5") shouldBe Triple("C", "5", "")
        Chord.tokenize("C4") shouldBe Triple("C", "4", "")
        Chord.tokenize("C4|\n") shouldBe Triple("", "C4|\n", "")

        // With bass
        Chord.tokenize("Cmaj7/G") shouldBe Triple("C", "maj7", "G")
        Chord.tokenize("bb6/a##") shouldBe Triple("Bb", "6", "A##")
        Chord.tokenize("bb6/a##5") shouldBe Triple("Bb", "6/a##5", "")
    }

    "getChord properties" {
        val chord = Chord.getChord("maj7", "G4", "G4")
        chord.empty shouldBe false
        chord.name shouldBe "G major seventh"
        chord.symbol shouldBe "Gmaj7"
        chord.tonic shouldBe "G"
        chord.root shouldBe "G"
        chord.bass shouldBe ""
        chord.rootDegree shouldBe 1
        chord.setNum shouldBe 2193
        chord.type shouldBe "major seventh"
        chord.aliases shouldBe listOf("maj7", "Δ", "ma7", "M7", "Maj7", "^7")
        chord.chroma shouldBe "100010010001"
        chord.intervals shouldBe listOf("1P", "3M", "5P", "7M")
        chord.normalized shouldBe "100010010001"
        chord.notes shouldBe listOf("G", "B", "D", "F#")
        chord.quality shouldBe ChordQuality.Major
    }

    "first inversion" {
        val chord = Chord.getChord("maj7", "G4", "B4")
        chord.empty shouldBe false
        chord.name shouldBe "G major seventh over B"
        chord.symbol shouldBe "Gmaj7/B"
        chord.tonic shouldBe "G"
        chord.root shouldBe "B"
        chord.bass shouldBe "B"
        chord.rootDegree shouldBe 2
        chord.setNum shouldBe 2193
        chord.type shouldBe "major seventh"
        chord.intervals shouldBe listOf("3M", "5P", "7M", "8P")
        chord.notes shouldBe listOf("B", "D", "F#", "G")
    }

    "first inversion without octave" {
        val chord = Chord.getChord("maj7", "G", "B")
        chord.empty shouldBe false
        chord.name shouldBe "G major seventh over B"
        chord.symbol shouldBe "Gmaj7/B"
        chord.tonic shouldBe "G"
        chord.root shouldBe "B"
        chord.bass shouldBe "B"
        chord.rootDegree shouldBe 2
        chord.setNum shouldBe 2193
        chord.type shouldBe "major seventh"
        chord.intervals shouldBe listOf("3M", "5P", "7M", "8P")
        chord.notes shouldBe listOf("B", "D", "F#", "G")
    }

    "second inversion" {
        val chord = Chord.getChord("maj7", "G4", "D5")
        chord.empty shouldBe false
        chord.name shouldBe "G major seventh over D"
        chord.symbol shouldBe "Gmaj7/D"
        chord.tonic shouldBe "G"
        chord.root shouldBe "D"
        chord.bass shouldBe "D"
        chord.rootDegree shouldBe 3
        chord.setNum shouldBe 2193
        chord.intervals shouldBe listOf("5P", "7M", "8P", "10M")
        chord.notes shouldBe listOf("D", "F#", "G", "B")
    }

    "rootDegrees" {
        Chord.getChord("maj7", "C", "C").rootDegree shouldBe 1
        Chord.getChord("maj7", "C", "D").rootDegree shouldBe 0
    }

    "without tonic nor root" {
        val chord = Chord.getChord("dim")
        chord.symbol shouldBe "dim"
        chord.name shouldBe "diminished"
        chord.tonic shouldBe ""
        chord.root shouldBe ""
        chord.bass shouldBe ""
        chord.rootDegree shouldBe 0
        chord.type shouldBe "diminished"
        chord.aliases shouldBe listOf("dim", "°", "o")
        chord.chroma shouldBe "100100100000"
        chord.empty shouldBe false
        chord.intervals shouldBe listOf("1P", "3m", "5d")
        chord.normalized shouldBe "100000100100"
        chord.notes shouldBe emptyList()
        chord.quality shouldBe ChordQuality.Diminished
        chord.setNum shouldBe 2336
    }

    "chord get" {
        val c = Chord.get("Cmaj7")
        c.empty shouldBe false
        c.symbol shouldBe "Cmaj7"
        c.name shouldBe "C major seventh"
        c.tonic shouldBe "C"
        c.root shouldBe ""
        c.bass shouldBe ""
        c.rootDegree shouldBe 0
        c.setNum shouldBe 2193
        c.type shouldBe "major seventh"
        c.aliases shouldBe listOf("maj7", "Δ", "ma7", "M7", "Maj7", "^7")
        c.chroma shouldBe "100010010001"
        c.intervals shouldBe listOf("1P", "3M", "5P", "7M")
        c.normalized shouldBe "100010010001"
        c.notes shouldBe listOf("C", "E", "G", "B")
        c.quality shouldBe ChordQuality.Major

        Chord.get("hello").empty shouldBe true
        Chord.get("").empty shouldBe true
        Chord.get("C").name shouldBe "C major"
    }

    "chord with bass, without root" {
        val c = Chord.get("C/Bb")
        c.aliases shouldBe listOf("M", "^", "", "maj")
        c.bass shouldBe "Bb"
        c.chroma shouldBe "100010010000"
        c.empty shouldBe false
        c.intervals shouldBe listOf("-2M", "1P", "3M", "5P")
        c.name shouldBe "C major over Bb"
        c.normalized shouldBe "100001000100"
        c.notes shouldBe listOf("Bb", "C", "E", "G")
        c.quality shouldBe ChordQuality.Major
        c.root shouldBe ""
        c.rootDegree shouldBe 0
        c.setNum shouldBe 2192
        c.symbol shouldBe "C/Bb"
        c.tonic shouldBe "C"
        c.type shouldBe "major"
    }

    "notes property" {
        Chord.get("Cmaj7").notes shouldBe listOf("C", "E", "G", "B")
        Chord.get("Eb7add6").notes shouldBe listOf("Eb", "G", "Bb", "Db", "C")
        Chord.get(listOf("C4", "maj7")).notes shouldBe listOf("C", "E", "G", "B")
        Chord.get("C7").notes shouldBe listOf("C", "E", "G", "Bb")
        Chord.get("Cmaj7#5").notes shouldBe listOf("C", "E", "G#", "B")
        Chord.get("blah").notes shouldBe emptyList()
    }

    "chordScales" {
        Chord.chordScales("C7b9") shouldBe listOf(
            "phrygian dominant",
            "flamenco",
            "spanish heptatonic",
            "half-whole diminished",
            "chromatic"
        )
    }

    "transposeChord" {
        Chord.transpose("Eb7b9", "5P") shouldBe "Bb7b9"
        Chord.transpose("7b9", "5P") shouldBe "7b9"
        Chord.transpose("Cmaj7/B", "P5") shouldBe "Gmaj7/F#"
    }

    "extendedChords" {
        val expected = "Cmaj#4 Cmaj7#9#11 Cmaj9 CM7add13 Cmaj13 Cmaj9#11 CM13#11 CM7b9".split(" ").sorted()
        Chord.extended("CMaj7").sorted() shouldBe expected
    }

    "reducedChords" {
        Chord.reduced("CMaj7") shouldBe listOf("C5", "CM")
    }

    "chordNotes" {
        Chord.notes("Cmaj7") shouldBe listOf("C", "E", "G", "B")
        Chord.notes("maj7") shouldBe emptyList()
        Chord.notes("maj7", "C4") shouldBe listOf("C4", "E4", "G4", "B4")
        Chord.notes("Cmaj7", "C4") shouldBe listOf("C4", "E4", "G4", "B4")
        Chord.notes("Cmaj7", "D4") shouldBe listOf("D4", "F#4", "A4", "C#5")
        Chord.notes("C/Bb", "D4") shouldBe listOf("C4", "D4", "F#4", "A4")
    }

    "chordDegrees" {
        val degreesC = Chord.degrees("C")
        listOf(1, 2, 3, 4).map { degreesC(it) } shouldBe listOf("C", "E", "G", "C")

        val degreesCM_C4 = Chord.degrees("CM", "C4")
        listOf(1, 2, 3, 4).map { degreesCM_C4(it) } shouldBe listOf("C4", "E4", "G4", "C5")

        val degreesCm6_C4 = Chord.degrees("Cm6", "C4")
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map { degreesCm6_C4(it) } shouldBe
                listOf("C4", "Eb4", "G4", "A4", "C5", "Eb5", "G5", "A5", "C6", "Eb6")

        val degreesC_B = Chord.degrees("C/B")
        listOf(1, 2, 3, 4).map { degreesC_B(it) } shouldBe listOf("B", "C", "E", "G")

        listOf(-1, -2, -3).map { degreesC(it) } shouldBe listOf("G", "E", "C")
        listOf(-1, -2, -3).map { degreesCM_C4(it) } shouldBe listOf("G3", "E3", "C3")
    }

    "chordSteps" {
        val steps = Chord.steps("aug", "C4")
        listOf(-3, -2, -1, 0, 1, 2, 3).map { steps(it) } shouldBe
                listOf("C3", "E3", "G#3", "C4", "E4", "G#4", "C5")
    }
})
