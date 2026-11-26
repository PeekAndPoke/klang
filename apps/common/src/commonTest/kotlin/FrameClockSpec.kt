/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FrameClockSpec : StringSpec({

    "frames count while the window is there, and add up their time; the first, before the drawing began, does not" {
        val clock = FrameClock(there = true)

        listOf(3000.0, 16.0, 17.0, 16.0).map { clock.frame(it) } shouldBe listOf(false, true, true, true)
        clock.drawnMs shouldBe 49.0
    }

    "a clock that starts with the window away counts nothing until it is back" {
        val clock = FrameClock(there = false)

        clock.frame(16.0) shouldBe false
        clock.back()
        clock.frame(9000.0) shouldBe false
        clock.frame(16.0) shouldBe true
        clock.drawnMs shouldBe 16.0
    }

    "away, no frame counts; back, the frame across the time away does not count, the next ones do" {
        val clock = FrameClock(there = true)

        clock.frame(16.0)

        clock.frame(16.0)
        clock.away()
        clock.frame(16.0) shouldBe false
        clock.frame(16.0) shouldBe false
        clock.back()
        // the frame that spans a hundred seconds away
        clock.frame(100_000.0) shouldBe false
        clock.frame(17.0) shouldBe true
        clock.drawnMs shouldBe 33.0
    }

    "back without having been away drops nothing" {
        val clock = FrameClock(there = true)

        clock.frame(16.0)

        clock.back()
        clock.frame(16.0) shouldBe true
    }

    "away twice and back once: one frame dropped" {
        val clock = FrameClock(there = true)

        clock.frame(16.0)

        clock.away()
        clock.away()
        clock.back()
        clock.frame(500.0) shouldBe false
        clock.frame(16.0) shouldBe true
        clock.drawnMs shouldBe 16.0
    }

    "a frame time that is no number, or not above 0, counts nothing" {
        val clock = FrameClock(there = true)

        clock.frame(16.0)

        clock.frame(Double.NaN) shouldBe false
        clock.frame(0.0) shouldBe false
        clock.frame(-5.0) shouldBe false
        clock.frame(Double.POSITIVE_INFINITY) shouldBe false
        clock.drawnMs shouldBe 0.0
    }
})
