/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.math.sqrt

/** The dial's geometry (KLANG-00338): a 270 degree sweep from bottom left over the top to bottom right. */
class DialSpec : StringSpec({

    fun distance(p: Pair<Double, Double>): Double {
        val dx = p.first - 50.0
        val dy = p.second - 50.0

        return sqrt(dx * dx + dy * dy)
    }

    "a fraction is coerced into 0..1, and a NaN counts as 0" {
        Dial.clamp(0.3) shouldBe 0.3
        Dial.clamp(-1.0) shouldBe 0.0
        Dial.clamp(2.0) shouldBe 1.0
        Dial.clamp(Double.NaN) shouldBe 0.0
    }

    "the sweep starts bottom left, passes the top at the middle and ends bottom right" {
        val start = Dial.point(fraction = 0.0, radius = 40.0)
        val middle = Dial.point(fraction = 0.5, radius = 40.0)
        val end = Dial.point(fraction = 1.0, radius = 40.0)

        start.first shouldBeLessThan 50.0
        start.second shouldBeGreaterThan 50.0

        middle.first shouldBe (50.0 plusOrMinus 1e-9)
        middle.second shouldBe (10.0 plusOrMinus 1e-9)

        end.first shouldBeGreaterThan 50.0
        end.second shouldBeGreaterThan 50.0

        // The ends sit level with each other, mirrored around the centre
        start.second shouldBe (end.second plusOrMinus 1e-9)
        (start.first + end.first) shouldBe (100.0 plusOrMinus 1e-9)
    }

    "every point lies its radius from the centre" {
        for (f in listOf(0.0, 0.1, 0.37, 0.5, 0.9, 1.0)) {
            distance(Dial.point(fraction = f, radius = 33.0)) shouldBe (33.0 plusOrMinus 1e-9)
        }
    }

    "an arc of nothing is empty; up to 180 degrees it is a small arc, past them a large one" {
        Dial.arc(fraction = 0.0, radius = 40.0) shouldBe ""
        Dial.arc(fraction = Double.NaN, radius = 40.0) shouldBe ""

        // 0.6 of 270 degrees is 162, under 180; 0.7 is 189, over it
        Dial.arc(fraction = 0.6, radius = 40.0) shouldContain " 0 0 1 "
        Dial.arc(fraction = 0.7, radius = 40.0) shouldContain " 0 1 1 "

        // It starts at the sweep's start, bottom left
        Dial.arc(fraction = 1.0, radius = 40.0) shouldStartWith "M 21.72 78.28 "
    }

    "a knob's cap turns from -135 over 0 to 135 degrees" {
        Dial.capDegrees(0.0) shouldBe -135.0
        Dial.capDegrees(0.5) shouldBe 0.0
        Dial.capDegrees(1.0) shouldBe 135.0
        Dial.capDegrees(Double.NaN) shouldBe -135.0
    }
})
