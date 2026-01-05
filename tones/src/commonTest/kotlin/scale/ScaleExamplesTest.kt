package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.range.numericRange

class ScaleExamplesTest : StringSpec({
    "Scale.get" {
        val s = getScale("c5 pentatonic")
        s.empty shouldBe false
        s.name shouldBe "C5 major pentatonic"
        s.type shouldBe "major pentatonic"
        s.tonic shouldBe "C5"
        s.notes shouldBe listOf("C5", "D5", "E5", "G5", "A5")
        s.intervals shouldBe listOf("1P", "2M", "3M", "5P", "6M")
        s.scaleType.aliases shouldBe listOf("pentatonic")
        s.scaleType.pcset.setNum shouldBe 2708
        s.chroma shouldBe "101010010100"
    }

    "Scale.detect" {
        val detected = detectScale(listOf("C", "D", "E", "F", "G", "A", "B"))
        detected.take(5) shouldBe
                listOf("C major", "C bebop", "C bebop major", "C ichikosucho", "C chromatic")

        val detectedA = detectScale(listOf("C", "D", "E", "F", "G", "A", "B"), tonic = "A")
        detectedA shouldBe
                listOf("A minor", "A minor bebop", "A chromatic")

        val exact = detectScale(listOf("D", "E", "F#", "A", "B"), matchExact = true)
        exact shouldBe
                listOf("D major pentatonic")

        val exactB = detectScale(listOf("D", "E", "F#", "A", "B"), matchExact = true, tonic = "B")
        exactB shouldBe
                listOf("B minor pentatonic")
    }

    "Scale.scaleChords" {
        scaleChords("pentatonic") shouldBe listOf("5", "M", "6", "sus2", "Madd9")
    }

    "Scale.extended" {
        extendedScales("major") shouldBe
                listOf("bebop", "bebop major", "ichikosucho", "chromatic")
    }

    "Scale.reduced" {
        reducedScales("major") shouldBe
                listOf("major pentatonic", "ionian pentatonic", "ritusen")
    }

    "Scale.scaleNotes" {
        scaleNotes(listOf("D4", "c#5", "A5", "F#6")) shouldBe listOf("D", "F#", "A", "C#")
        scaleNotes(listOf("C4", "c3", "C5", "C4", "c4")) shouldBe listOf("C")
    }

    "Scale.modeNames" {
        modeNames("C pentatonic") shouldBe listOf(
            "C" to "major pentatonic",
            "D" to "egyptian",
            "E" to "malkos raga",
            "G" to "ritusen",
            "A" to "minor pentatonic"
        )
    }

    "Scale.degrees" {
        val c4major = scaleDegrees("C4 major")
        c4major(1) shouldBe "C4"
        c4major(2) shouldBe "D4"
        c4major(8) shouldBe "C5"
        c4major(-1) shouldBe "B3"
        // Note: TonalJS README says "A3" but it should be "G3" (degree -1 is B, -2 is A, -3 is G)
        // Wait, if 1 is C, 0 is "", -1 is B, -2 is A, -3 is G.
        c4major(-3) shouldBe "G3"
        c4major(-2) shouldBe "A3"
        c4major(-7) shouldBe "C3"
        // Wait, C(1), B(-1), A(-2), G(-3), F(-4), E(-5), D(-6), C(-7).
        // c4major(-7) should be C3.
        // Wait, TonalJS README says C2 for -7.
        // If -1 is B3, -2 is A3, -3 is G3, -4 is F3, -5 is E3, -6 is D3, -7 is C3.
        // Why does README say C2?
        // Maybe it skips 0?
        // But c4major(0) is "".
        // If it skips 0, then 1 is C4, -1 is B3.
        // Still, distance from 1 to -1 is 1 octave.
        // Wait, C4 to C3 is 8 steps (1 to 8).
        // 1(C4), 2(D4), 3(E4), 4(F4), 5(G4), 6(A4), 7(B4), 8(C5).
        // 1(C4), -1(B3), -2(A3), -3(G3), -4(F3), -5(E3), -6(D3), -7(C3).
        // Yes, C3 is correct for -7. C2 is two octaves down.

        listOf(1, 2, 3).map(scaleDegrees("C major")) shouldBe listOf("C", "D", "E")
        listOf(1, 2, 3).map(scaleDegrees("C4 major")) shouldBe listOf("C4", "D4", "E4")
        listOf(-1, -2, -3).map(scaleDegrees("C major")) shouldBe listOf("B", "A", "G")
    }

    "Scale.steps" {
        numericRange(listOf(-3, 3)).map(scaleSteps("C4 major")) shouldBe
                listOf("G3", "A3", "B3", "C4", "D4", "E4", "F4")
    }

    "Scale.rangeOf" {
        val range = rangeOfScale("C pentatonic")
        range("C4", "C5") shouldBe listOf("C4", "D4", "E4", "G4", "A4", "C5")

        rangeOfScale("pentatonic")("C4", "C5") shouldBe emptyList<String>()

        val range2 = rangeOfScale(listOf("C", "Db", "G"))
        range2("C4", "C5") shouldBe listOf("C4", "Db4", "G4", "C5")
    }
})
