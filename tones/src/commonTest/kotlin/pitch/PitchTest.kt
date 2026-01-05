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
        listOf(C, Cs, Cb, A).map { height(it) } shouldBe listOf(-1200, -1199, -1201, -1191)
        listOf(C4, A4, Gs6).map { height(it) } shouldBe listOf(48, 57, 80)
        listOf(P5, P_5).map { height(it) } shouldBe listOf(7, -7)
    }

    "midi" {
        listOf(C, Cs, Cb, A).map { midi(it) } shouldBe listOf(null, null, null, null)
        listOf(C4, A4, Gs6).map { midi(it) } shouldBe listOf(60, 69, 92)
    }

    "chroma" {
        listOf(C, Cs, Cb, A).map { chroma(it) } shouldBe listOf(0, 1, 11, 9)
        listOf(C4, A4, Gs6).map { chroma(it) } shouldBe listOf(0, 9, 8)
        listOf(P5, P_5).map { chroma(it) } shouldBe listOf(7, 7)
    }

    "coordinates" {
        coordinates(C) shouldBe PitchCoordinates.PitchClass(0)
        coordinates(A) shouldBe PitchCoordinates.PitchClass(3)
        coordinates(Cs) shouldBe PitchCoordinates.PitchClass(7)
        coordinates(Cb) shouldBe PitchCoordinates.PitchClass(-7)

        // notes
        coordinates(C4) shouldBe PitchCoordinates.Note(0, 4)
        coordinates(A4) shouldBe PitchCoordinates.Note(3, 3)

        // intervals
        coordinates(P5) shouldBe PitchCoordinates.Interval(1, 0, 1)
        coordinates(P_5) shouldBe PitchCoordinates.Interval(-1, 0, -1)
    }

    "pitch" {
        pitch(PitchCoordinates.PitchClass(0)) shouldBe C
        pitch(PitchCoordinates.PitchClass(7)) shouldBe Cs
    }
})
