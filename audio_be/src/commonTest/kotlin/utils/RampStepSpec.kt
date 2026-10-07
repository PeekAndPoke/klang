/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** [rampStep]: the per-frame step of a linear ramp across a block, the frame count read as at least 1. */
class RampStepSpec : StringSpec({

    "the step walks from the start to the end in the given frames" {
        rampStep(from = 1.0, to = 2.0, frames = 4) shouldBe 0.25
        rampStep(from = 2.0, to = 1.0, frames = 4) shouldBe -0.25

        var m = 1.0
        val dm = rampStep(from = 1.0, to = 2.0, frames = 4)

        repeat(4) { m += dm }

        m shouldBe 2.0
    }

    "an empty or negative frame count steps the whole way at once" {
        rampStep(from = 1.0, to = 2.0, frames = 1) shouldBe 1.0
        rampStep(from = 1.0, to = 2.0, frames = 0) shouldBe 1.0
        rampStep(from = 1.0, to = 2.0, frames = -3) shouldBe 1.0
    }

    "the step is (to - from) / frames in that order, bit for bit" {
        // Reassociated as to / frames - from / frames it would round to 0.03333333333333338.
        rampStep(from = 1.0, to = 1.1, frames = 3).toRawBits() shouldBe 0.03333333333333336.toRawBits()
    }
})
