/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

private data class At(override val t: Double) : Timed

class LiveActMathSpec : StringSpec({

    "smooth is 0 below 0, 1 above 1, a half in the middle, and flat at both ends" {
        smooth(-3.0) shouldBe 0.0
        smooth(4.0) shouldBe 1.0
        smooth(0.5) shouldBe 0.5
        smooth(0.25) shouldBe (0.15625 plusOrMinus 1e-12)
        smooth(0.01) shouldBe (0.000298 plusOrMinus 1e-9)
    }

    "smoother is flatter at its ends than smooth, and the same in the middle" {
        smoother(0.5) shouldBe 0.5
        smoother(0.25) shouldBe (0.103515625 plusOrMinus 1e-12)
        smoother(-1.0) shouldBe 0.0
        smoother(2.0) shouldBe 1.0
    }

    "valueNoise gives the value at a whole number and eases between two" {
        val at = { i: Int -> if (i == 3) 1.0 else 0.0 }

        valueNoise(3.0, at) shouldBe 1.0
        valueNoise(2.5, at) shouldBe 0.5
        valueNoise(2.25, at) shouldBe (0.15625 plusOrMinus 1e-12)
        valueNoise(-0.5) { i -> if (i == -1) 1.0 else 0.0 } shouldBe 0.5
    }

    "around gives the one at or before a time and the one after it" {
        val list = listOf(At(1.0), At(2.0), At(3.0))

        list.around(0.5) shouldBe (null to At(1.0))
        list.around(2.0) shouldBe (At(2.0) to At(3.0))
        list.around(2.5) shouldBe (At(2.0) to At(3.0))
        list.around(9.0) shouldBe (At(3.0) to null)
        emptyList<At>().around(1.0) shouldBe (null to null)
    }

    "lastIndexAt is the index of the last one at or before a time, the later of two at the same time" {
        val list = listOf(At(1.0), At(2.0), At(2.0), At(3.0))

        list.lastIndexAt(0.5) shouldBe -1
        list.lastIndexAt(2.0) shouldBe 2
        list.lastIndexAt(2.9) shouldBe 2
        list.lastIndexAt(3.0) shouldBe 3
    }

    "the switches of a query string: flags, values, numbers and triples" {
        val switches = Switches.parse("?seed=7&still&t=62.1&cam=1,2.5,-3&at=1,2")

        switches.has(Switch.Still) shouldBe true
        switches.has(Switch.NoHud) shouldBe false
        switches[Switch.Seed] shouldBe "7"
        switches[Switch.Still] shouldBe ""
        switches.double(Switch.Time) shouldBe 62.1
        switches.double(Switch.Still) shouldBe null
        switches.triple(Switch.Cam) shouldBe Triple(1.0, 2.5, -3.0)
        switches.triple(Switch.At) shouldBe null
        Switches.parse("").has(Switch.Seed) shouldBe false
    }

    "a show's chance is the same under the same seed and another under another" {
        val a = Show(seed = 3)
        val b = Show(seed = 3)
        val c = Show(seed = 4)

        a.own(5, 11.0) shouldBe b.own(5, 11.0)
        (a.own(5, 11.0) == c.own(5, 11.0)) shouldBe false
        a.own(5, 11.0) shouldBe a.own(5.0, 11.0)
        a.bpm shouldBe 120.0
        a.bar shouldBe 2.0
    }

    "a head turns fully to what is before or beside someone, and not at all to what is behind them" {
        inSight(0.0) shouldBe 1.0
        inSight(1.2) shouldBe 1.0
        inSight(-1.2) shouldBe 1.0

        // between 70 and 95 degrees to the side it fades, the same on both sides and never back up
        val fade = (0..50).map { inSight(1.22 + it * 0.009) }

        fade[25] shouldBeGreaterThan 0.0
        fade[25] shouldBeLessThan 1.0
        fade.zipWithNext().all { (a, b) -> b <= a } shouldBe true
        inSight(-1.4835) shouldBe inSight(1.4835)
        inSight(1.7) shouldBe 0.0
        inSight(-2.55) shouldBe 0.0
        inSight(3.14159) shouldBe 0.0
    }

    "beyond: nothing well within the easy reach, what lies past it far out, and a round bend between" {
        beyond(x = 0.10, easy = 0.2, soft = 0.05) shouldBe 0.0
        beyond(x = 0.15, easy = 0.2, soft = 0.05) shouldBe 0.0
        beyond(x = 0.30, easy = 0.2, soft = 0.05) shouldBe (0.10 plusOrMinus 1e-12)
        beyond(x = 0.25, easy = 0.2, soft = 0.05) shouldBe (0.05 plusOrMinus 1e-12)
        // right at the edge: a quarter of the bend's width
        beyond(x = 0.20, easy = 0.2, soft = 0.05) shouldBe (0.0125 plusOrMinus 1e-12)
        // the same on the other side, with its sign
        beyond(x = -0.30, easy = 0.2, soft = 0.05) shouldBe (-0.10 plusOrMinus 1e-12)
        beyond(x = -0.20, easy = 0.2, soft = 0.05) shouldBe (-0.0125 plusOrMinus 1e-12)

        // it never falls as x grows, and never steps
        val rise = (0..400).map { beyond(x = -0.4 + it * 0.002, easy = 0.2, soft = 0.05) }

        rise.zipWithNext().all { (a, b) -> b >= a && b - a <= 0.002 + 1e-12 } shouldBe true
    }

    "beyond with no bend: a sharp corner at the edge" {
        beyond(x = 0.20, easy = 0.2, soft = 0.0) shouldBe 0.0
        beyond(x = 0.25, easy = 0.2, soft = 0.0) shouldBe (0.05 plusOrMinus 1e-12)
        beyond(x = -0.25, easy = 0.2, soft = 0.0) shouldBe (-0.05 plusOrMinus 1e-12)
    }
})
