/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** A graph scales to the highest value in view: the multiple of 20 above it. */
class MotorHistoryGraphsSpec : StringSpec({

    "the top is the multiple of 20 above the highest value" {
        MotorHistoryGraphs.topFor(9.0) shouldBe 20.0
        MotorHistoryGraphs.topFor(23.0) shouldBe 40.0
        MotorHistoryGraphs.topFor(46.0) shouldBe 60.0
        MotorHistoryGraphs.topFor(150.0) shouldBe 160.0
    }

    "a value right on a multiple gets the next one: the line never touches the top" {
        MotorHistoryGraphs.topFor(40.0) shouldBe 60.0
    }

    "a quiet graph ends at the first step" {
        MotorHistoryGraphs.topFor(0.0) shouldBe 20.0
        MotorHistoryGraphs.topFor(Double.NaN) shouldBe 20.0
    }
})
