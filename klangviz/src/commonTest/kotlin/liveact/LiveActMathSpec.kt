/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
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

        switches.has("still") shouldBe true
        switches.has("nohud") shouldBe false
        switches["seed"] shouldBe "7"
        switches["still"] shouldBe ""
        switches.double("t") shouldBe 62.1
        switches.double("still") shouldBe null
        switches.triple("cam") shouldBe Triple(1.0, 2.5, -3.0)
        switches.triple("at") shouldBe null
        Switches.parse("").has("seed") shouldBe false
    }

    "a show's chance is the same under the same seed and another under another" {
        val a = Show(seed = 3, song = null, loudness = null)
        val b = Show(seed = 3, song = null, loudness = null)
        val c = Show(seed = 4, song = null, loudness = null)

        a.own(5, 11.0) shouldBe b.own(5, 11.0)
        (a.own(5, 11.0) == c.own(5, 11.0)) shouldBe false
        a.own(5, 11.0) shouldBe a.own(5.0, 11.0)
        a.bpm shouldBe 120.0
        a.bar shouldBe 2.0
    }

    "the loudness at a time: the nearest measured value, the ends held, the burst over the last six" {
        val loudness = Loudness(
            step = 0.5, offset = 0.0,
            level = listOf(0.2, 0.8, 0.4), slow = listOf(0.2, 0.2, 0.4),
            low = listOf(0.1, 0.2, 0.3), mid = listOf(0.4, 0.5, 0.6), high = listOf(0.7, 0.8, 0.9),
        )

        loudness.at(-5.0).level shouldBe 0.2
        loudness.at(99.0).level shouldBe 0.4
        loudness.at(0.5).low shouldBe 0.2
        loudness.at(0.5).slow shouldBe 0.2
        // at index 1 the level is 0.6 above the slow one; the five before it (all index 0) are not above
        loudness.at(0.5).burst shouldBe (0.1 plusOrMinus 1e-12)
        loudness.at(1.0).burst shouldBe (0.1 plusOrMinus 1e-12)
    }
})
