/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.ultra.maths.Ease

/**
 * [easeInOutCubic]: the in-out cubic, bit for bit the library curve the solo ramp called before (audit item B4.17).
 * The library is the oracle here; the render path no longer calls it.
 */
class EaseInOutCubicSpec : StringSpec({

    "matches Ease.InOut.cubic bit for bit over 0 to 1, both halves and the middle" {
        val steps = 100_000

        for (i in 0..steps) {
            val x = i.toDouble() / steps
            val want = Ease.InOut.cubic(x)
            val got = easeInOutCubic(x)

            if (got.toRawBits() != want.toRawBits()) {
                withClue("x = $x") { got shouldBe want }
            }
        }
    }

    "matches it on the progress values a block-stepped ramp lands on" {
        // A 1.5 s ramp stepped by 128-frame blocks at 48 kHz, the solo ramp's own stepping.
        val dt = 128.0 / 48_000.0
        var progress = 0.0

        while (progress < 1.0) {
            progress += dt / 1.5
            easeInOutCubic(progress).toRawBits() shouldBe Ease.InOut.cubic(progress).toRawBits()
        }
    }

    "0 at 0, a half at the middle, 1 at 1, and the halves meet there" {
        easeInOutCubic(0.0) shouldBe 0.0
        easeInOutCubic(0.5) shouldBe 0.5
        easeInOutCubic(1.0) shouldBe 1.0
        easeInOutCubic(0.25) shouldBe 0.0625
        easeInOutCubic(0.75) shouldBe 0.9375
    }
})
