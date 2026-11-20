/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class LightsUpSpec : StringSpec({

    "the light comes up from pitch black over ten seconds, eased at both ends, and stays up" {
        LightsUp.level(since = 0.0) shouldBe 0.0
        // a quarter of the way: 0.25 * 0.25 * (3 - 0.5)
        LightsUp.level(since = 2.5) shouldBe (0.15625 plusOrMinus 1e-12)
        LightsUp.level(since = 5.0) shouldBe (0.5 plusOrMinus 1e-12)
        LightsUp.level(since = 7.5) shouldBe (0.84375 plusOrMinus 1e-12)
        LightsUp.level(since = 10.0) shouldBe 1.0
        LightsUp.level(since = 600.0) shouldBe 1.0
    }

    "a time below 0, or no number, is pitch black" {
        LightsUp.level(since = -3.0) shouldBe 0.0
        LightsUp.level(since = Double.NaN) shouldBe 0.0
        LightsUp.level(since = Double.NEGATIVE_INFINITY) shouldBe 0.0
    }

    "the time is counted frame by frame, a long frame (a hitch) as a quarter of a second, no frame of no number" {
        LightsUp.advance(since = 1.0, frame = 0.016) shouldBe (1.016 plusOrMinus 1e-12)
        LightsUp.advance(since = 1.0, frame = 3.0) shouldBe 1.25
        LightsUp.advance(since = 1.0, frame = -0.5) shouldBe 1.0
        LightsUp.advance(since = 1.0, frame = Double.NaN) shouldBe 1.0
    }
})
