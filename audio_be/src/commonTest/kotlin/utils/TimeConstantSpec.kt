/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/** [timeConstantCoeff]: `1 - exp(-1 / (t * sr))`, the coefficient of a one-pole with time constant `t`. */
class TimeConstantSpec : StringSpec({

    "one second at one sample per second is 1 - 1/e" {
        timeConstantCoeff(timeSeconds = 1.0, sampleRate = 1.0) shouldBe (0.6321205588285577 plusOrMinus 1e-15)
        timeConstantCoeff(timeSeconds = 0.5, sampleRate = 2.0) shouldBe timeConstantCoeff(timeSeconds = 1.0, sampleRate = 1.0)
    }

    "a one-pole with it covers 1 - 1/e of a step in the time constant" {
        val c = timeConstantCoeff(timeSeconds = 0.01, sampleRate = 48000.0)
        var y = 0.0

        repeat(480) { y += c * (1.0 - y) }

        y shouldBe (0.6321205588285577 plusOrMinus 1e-9)
    }

    "a zero time follows at once, a negative time gives a coefficient below 0" {
        timeConstantCoeff(timeSeconds = 0.0, sampleRate = 48000.0) shouldBe 1.0
        timeConstantCoeff(timeSeconds = -0.01, sampleRate = 48000.0) shouldBeLessThan 0.0
    }

    "an infinite time gives exactly 0: the smoother never moves" {
        timeConstantCoeff(timeSeconds = Double.POSITIVE_INFINITY, sampleRate = 48000.0) shouldBe 0.0
    }
})
