/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** A graph without a range of its own scales to the highest value in view: a round top, a little over it. */
class MotorHistoryGraphsSpec : StringSpec({

    "the top is the next round number a little over the highest value" {
        MotorHistoryGraphs.topFor(9.0) shouldBe 10.0
        MotorHistoryGraphs.topFor(12.0) shouldBe 20.0
        MotorHistoryGraphs.topFor(23.0) shouldBe 50.0
        MotorHistoryGraphs.topFor(46.0) shouldBe 100.0
        MotorHistoryGraphs.topFor(150.0) shouldBe 200.0
    }

    "a quiet graph ends at the least top, not on its noise" {
        MotorHistoryGraphs.topFor(0.0) shouldBe 5.0
        MotorHistoryGraphs.topFor(3.0) shouldBe 5.0
        MotorHistoryGraphs.topFor(Double.NaN) shouldBe 5.0
    }
})
