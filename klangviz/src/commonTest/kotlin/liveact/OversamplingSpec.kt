/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OversamplingSpec : StringSpec({

    "the steps are 0.5, 1, 2 and 4 samples for each pixel" {
        Oversampling.STEPS shouldBe listOf(0.5, 1.0, 2.0, 4.0)
    }

    "the address's oversample is one of the steps, 0.5 too; anything else is 1" {
        Oversampling.parse("0.5") shouldBe 0.5
        Oversampling.parse("2") shouldBe 2.0
        Oversampling.parse("4.0") shouldBe 4.0
        Oversampling.parse("3") shouldBe 1.0
        Oversampling.parse("0.25") shouldBe 1.0
        Oversampling.parse("x") shouldBe 1.0
        Oversampling.parse(null) shouldBe 1.0
    }

    "the key o cycles through all four, 0.5 among them" {
        Oversampling.next(0.5) shouldBe 1.0
        Oversampling.next(1.0) shouldBe 2.0
        Oversampling.next(2.0) shouldBe 4.0
        Oversampling.next(4.0) shouldBe 0.5
    }

    "the step down by itself goes one step, never below 1" {
        Oversampling.below(4.0) shouldBe 2.0
        Oversampling.below(2.0) shouldBe 1.0
        Oversampling.below(1.0) shouldBe null
        Oversampling.below(0.5) shouldBe null
    }

    "the picture follows the frame rate at 1 and at 0.5, not above" {
        Oversampling.follows(0.5) shouldBe true
        Oversampling.follows(1.0) shouldBe true
        Oversampling.follows(2.0) shouldBe false
        Oversampling.follows(4.0) shouldBe false
    }

    "the labels" {
        Oversampling.STEPS.map { Oversampling.label(it) } shouldBe listOf("0.5x", "1x", "2x", "4x")
    }
})
