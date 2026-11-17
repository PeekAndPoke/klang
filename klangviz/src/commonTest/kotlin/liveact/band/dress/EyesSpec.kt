/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.exp

class EyesSpec : StringSpec({

    val eyes = EyeWarmth(cools = 7.0, hold = 2.0)

    /** Notes [every] seconds apart from 0 to [until]. */
    fun steady(every: Double, until: Double): List<Double> = generateSequence(0.0) { it + every }.takeWhile { it <= until }.toList()

    "who has not played has cold eyes: before the first note, with no notes at all" {
        eyes.at(notes = emptyList(), t = 10.0) shouldBe 0.0
        eyes.at(notes = listOf(5.0, 6.0), t = 4.9) shouldBe 0.0
    }

    "the longer they play the warmer: two thirds after the cooling time, never quite all" {
        val notes = steady(every = 0.5, until = 200.0)
        val warm = listOf(1.0, 3.0, 7.0, 14.0, 28.0, 100.0).map { eyes.at(notes = notes, t = it) }

        warm.zipWithNext().all { (a, b) -> b > a } shouldBe true
        warm[2] shouldBe (1 - exp(-1.0) plusOrMinus 1e-9)
        warm[3] shouldBe (1 - exp(-2.0) plusOrMinus 1e-9)
        warm.last() shouldBeLessThan 1.0
        warm.last() shouldBeGreaterThan 0.99
    }

    "how many notes a second they play does not matter, as long as they play on" {
        val fast = eyes.at(notes = steady(every = 0.125, until = 60.0), t = 20.0)
        val slow = eyes.at(notes = steady(every = 1.5, until = 60.0), t = 20.0)

        fast shouldBe (slow plusOrMinus 1e-9)
    }

    "they cool as soon as the playing stops: to a third in the cooling time" {
        val notes = steady(every = 0.5, until = 30.0)
        // the last note is held 2 s, to 32 s
        val atEnd = eyes.at(notes = notes, t = 32.0)

        eyes.at(notes = notes, t = 39.0) shouldBe (atEnd * exp(-1.0) plusOrMinus 1e-9)
        eyes.at(notes = notes, t = 120.0) shouldBeLessThan 0.001
    }

    "one note alone warms them a little, for as long as it is held, and no longer" {
        val one = listOf(10.0)

        eyes.at(notes = one, t = 11.0) shouldBe (1 - exp(-1.0 / 7.0) plusOrMinus 1e-9)
        // held 2 s, then cooling
        eyes.at(notes = one, t = 12.0) shouldBe (1 - exp(-2.0 / 7.0) plusOrMinus 1e-9)
        eyes.at(notes = one, t = 19.0) shouldBe ((1 - exp(-2.0 / 7.0)) * exp(-1.0) plusOrMinus 1e-9)
    }

    "notes far apart leave gaps in which they cool" {
        // a note every 6 s, held 2 s each: a third of the time
        val sparse = eyes.at(notes = steady(every = 6.0, until = 300.0), t = 200.0)

        sparse shouldBeLessThan 0.5
        sparse shouldBeGreaterThan 0.1
    }

    "cold eyes have their own colour, subtly, and glow as eyes at rest do, whatever their player sets" {
        EyeLook.of(warmth = 0.0, set = 2.0) shouldBe EyeLook(own = EyeLook.TINT, glow = EyeLook.REST_GLOW)
        EyeLook.of(warmth = 0.0, set = 20.0) shouldBe EyeLook(own = EyeLook.TINT, glow = EyeLook.REST_GLOW)
        // a tint, not the whole colour, and far more of it than hot eyes keep
        (EyeLook.TINT < 1.0) shouldBe true
        (EyeLook.TINT > 4 * EyeLook.HOT) shouldBe true
    }

    "from nine tenths warm on the eyes are almost white, and flare with what their player sets" {
        for (warmth in listOf(0.9, 0.95, 1.0)) {
            EyeLook.of(warmth = warmth, set = 2.0).own shouldBe (EyeLook.HOT plusOrMinus 1e-12)
            EyeLook.of(warmth = warmth, set = 2.0).glow shouldBe (EyeLook.GLOW plusOrMinus 1e-12)
            EyeLook.of(warmth = warmth, set = 12.0).glow shouldBe (EyeLook.GLOW + 10 * EyeLook.FLARE plusOrMinus 1e-12)
        }
    }

    "between, the colour goes steadily, half of the way at half the way" {
        val own = (0..90).map { EyeLook.of(warmth = it / 100.0, set = 2.0).own }

        own.zipWithNext().all { (a, b) -> b < a } shouldBe true
        EyeLook.of(warmth = 0.45, set = 2.0).own shouldBe ((EyeLook.TINT + EyeLook.HOT) / 2 plusOrMinus 1e-12)
        // a warmth beyond what there is does no harm
        EyeLook.of(warmth = -0.2, set = 2.0).own shouldBe EyeLook.TINT
        EyeLook.of(warmth = 1.7, set = 2.0).own shouldBe (EyeLook.HOT plusOrMinus 1e-12)
    }

    "a cooling time and a hold are times: none of them is 0 or less" {
        shouldThrow<IllegalArgumentException> { EyeWarmth(cools = 0.0) }
        shouldThrow<IllegalArgumentException> { EyeWarmth(hold = -1.0) }
    }
})
