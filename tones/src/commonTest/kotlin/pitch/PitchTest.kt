@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.tones.pitch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PitchTest : StringSpec({
    // Pitch classes
    val C = Pitch(step = 0, alt = 0)
    val Cs = Pitch(step = 0, alt = 1)
    val Cb = Pitch(step = 0, alt = -1)
    val A = Pitch(step = 5, alt = 0)

    // Notes
    val C4 = Pitch(step = 0, alt = 0, oct = 4)
    val A4 = Pitch(step = 5, alt = 0, oct = 4)
    val Gs6 = Pitch(step = 4, alt = 1, oct = 6)

    // Intervals
    val P5 = Pitch(step = 4, alt = 0, oct = 0, dir = 1)
    val P_5 = Pitch(step = 4, alt = 0, oct = 0, dir = -1)

    "height" {
        listOf(C, Cs, Cb, A).map { Pitch.height(it) } shouldBe listOf(-1200, -1199, -1201, -1191)
        listOf(C4, A4, Gs6).map { Pitch.height(it) } shouldBe listOf(48, 57, 80)
        listOf(P5, P_5).map { Pitch.height(it) } shouldBe listOf(7, -7)
    }

    "midi" {
        listOf(C, Cs, Cb, A).map { Pitch.midi(it) } shouldBe listOf(null, null, null, null)
        listOf(C4, A4, Gs6).map { Pitch.midi(it) } shouldBe listOf(60, 69, 92)
    }

    "chroma" {
        listOf(C, Cs, Cb, A).map { Pitch.chroma(it) } shouldBe listOf(0, 1, 11, 9)
        listOf(C4, A4, Gs6).map { Pitch.chroma(it) } shouldBe listOf(0, 9, 8)
        listOf(P5, P_5).map { Pitch.chroma(it) } shouldBe listOf(7, 7)
    }

    "coordinates" {
        Pitch.coordinates(C) shouldBe PitchCoordinates.PitchClass(0)
        Pitch.coordinates(A) shouldBe PitchCoordinates.PitchClass(3)
        Pitch.coordinates(Cs) shouldBe PitchCoordinates.PitchClass(7)
        Pitch.coordinates(Cb) shouldBe PitchCoordinates.PitchClass(-7)

        // notes
        Pitch.coordinates(C4) shouldBe PitchCoordinates.Note(0, 4)
        Pitch.coordinates(A4) shouldBe PitchCoordinates.Note(3, 3)

        // intervals
        Pitch.coordinates(P5) shouldBe PitchCoordinates.Interval(1, 0, 1)
        Pitch.coordinates(P_5) shouldBe PitchCoordinates.Interval(-1, 0, -1)
    }

    "pitch" {
        Pitch.pitch(PitchCoordinates.PitchClass(0)) shouldBe C
        Pitch.pitch(PitchCoordinates.PitchClass(7)) shouldBe Cs
    }
})
