/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** [sameKnob]: equal with the sign of zero told apart, or both NaN. */
class SameKnobSpec : StringSpec({

    "equal values are the same, unequal ones are not" {
        sameKnob(stored = 0.25, arriving = 0.25) shouldBe true
        sameKnob(stored = -12.0, arriving = -12.0) shouldBe true
        sameKnob(stored = 0.25, arriving = 0.5) shouldBe false
        sameKnob(stored = Double.POSITIVE_INFINITY, arriving = Double.POSITIVE_INFINITY) shouldBe true
        sameKnob(stored = Double.POSITIVE_INFINITY, arriving = Double.NEGATIVE_INFINITY) shouldBe false
    }

    "two NaNs are the same value, a NaN and a number are not, either way round" {
        sameKnob(stored = Double.NaN, arriving = Double.NaN) shouldBe true
        sameKnob(stored = Double.NaN, arriving = 1.0) shouldBe false
        sameKnob(stored = 1.0, arriving = Double.NaN) shouldBe false
    }

    "0.0 and -0.0 are told apart, each the same as itself" {
        sameKnob(stored = 0.0, arriving = -0.0) shouldBe false
        sameKnob(stored = -0.0, arriving = 0.0) shouldBe false
        sameKnob(stored = 0.0, arriving = 0.0) shouldBe true
        sameKnob(stored = -0.0, arriving = -0.0) shouldBe true
    }
})
