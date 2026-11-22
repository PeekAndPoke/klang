/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class MoonStopSpec : StringSpec({

    "the flight starts at the start, ends once round, and never goes back" {
        MoonStop.travel(0.0) shouldBe 0.0
        MoonStop.travel(1.0) shouldBe 1.0

        for (i in 0 until 1000) {
            MoonStop.travel((i + 1) / 1000.0) shouldBeGreaterThanOrEqual MoonStop.travel(i / 1000.0)
        }
    }

    "the camera rests half way round, with the look on the moon" {
        for (u in listOf(MoonStop.FROM, 0.5, MoonStop.TO)) {
            MoonStop.travel(u) shouldBe 0.5
            MoonStop.look(u) shouldBe 1.0
        }
    }

    "the look is on the band until shortly before the rest and again shortly after it" {
        MoonStop.look(0.0) shouldBe 0.0
        MoonStop.look(MoonStop.FROM - MoonStop.TURN) shouldBe (0.0 plusOrMinus 1e-9)
        MoonStop.look(MoonStop.TO + MoonStop.TURN) shouldBe (0.0 plusOrMinus 1e-9)
        MoonStop.look(1.0) shouldBe 0.0
    }

    "neither the way nor the look jumps" {
        for (i in 0 until 1000) {
            abs(MoonStop.travel((i + 1) / 1000.0) - MoonStop.travel(i / 1000.0)) shouldBeLessThan 0.005
            abs(MoonStop.look((i + 1) / 1000.0) - MoonStop.look(i / 1000.0)) shouldBeLessThan 0.02
        }
    }

    "what is no number is the start, looking at the band" {
        MoonStop.travel(Double.NaN) shouldBe 0.0
        MoonStop.look(Double.NaN) shouldBe 0.0
    }
})
