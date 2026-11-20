/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

class TailHelpersSpec : StringSpec({

    "the nearest point of a stick: inside it, and beyond its ends" {
        val out = DoubleArray(3)

        toSegment(px = 1.0, py = 0.5, pz = 0.0, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 2.0, bz = 0.0, out = out) shouldBe
            (1.0 plusOrMinus 1e-12)
        out[1] shouldBe (0.5 plusOrMinus 1e-12)

        toSegment(px = 0.0, py = 3.0, pz = 4.0, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 2.0, bz = 0.0, out = out) shouldBe
            (sqrt(17.0) plusOrMinus 1e-12)
        out[1] shouldBe (2.0 plusOrMinus 1e-12)

        toSegment(px = 0.0, py = -1.0, pz = 0.0, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 2.0, bz = 0.0, out = out) shouldBe
            (1.0 plusOrMinus 1e-12)
        out[1] shouldBe (0.0 plusOrMinus 1e-12)
    }

    "each point goes to the stick nearest to it" {
        val sticks = doubleArrayOf(
            0.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            1.0, 0.0, 0.0, 1.0, 1.0, 0.0,
        )
        val points = doubleArrayOf(
            0.2, 0.5, 0.0,
            0.9, 0.5, 0.0,
            0.6, 3.0, 0.0,
        )

        nearestSticks(points = points, sticks = sticks).toList() shouldBe listOf(0, 1, 1)
    }

    "how far points reach round a stick: the share of them within" {
        // ten points 1.0 down to 0.1 from the stick
        val points = DoubleArray(30) { k -> if (k % 3 == 0) (10 - k / 3) / 10.0 else if (k % 3 == 1) 0.5 else 0.0 }

        reachRound(points = points, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 1.0, bz = 0.0, share = 0.0) shouldBe
            (0.1 plusOrMinus 1e-12)
        reachRound(points = points, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 1.0, bz = 0.0, share = 1.0) shouldBe
            (1.0 plusOrMinus 1e-12)
        reachRound(points = points, ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 1.0, bz = 0.0, share = 0.75) shouldBe
            (0.7 plusOrMinus 1e-12)
        reachRound(points = DoubleArray(0), ax = 0.0, ay = 0.0, az = 0.0, bx = 0.0, by = 1.0, bz = 0.0, share = 0.75) shouldBe
            0.0
    }
})
