/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PictureQualitySpec : StringSpec({

    "a machine that holds its rate at 2x, on a plain display, starts at 2x" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 1.0) shouldBe 2
        // 0.9 of 60 is 54, but 55 must be held: 55 is enough
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 55.0, density = 1.0) shouldBe 2
    }

    "the 2x part must hold 55 frames and 0.9 of the 1x part" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 54.9, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 100.0, fps2x = 89.0, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 100.0, fps2x = 90.0, density = 1.0) shouldBe 2
    }

    "above 120 at 1x, the 2x part only has to hold 55" {
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = 60.0, density = 1.0) shouldBe 2
        PictureQuality.oversampleFor(fps1x = 120.0, fps2x = 60.0, density = 1.0) shouldBe 1
    }

    "without a 2x part there is no 2x" {
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = null, density = 1.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = Double.NaN, density = 1.0) shouldBe 1
    }

    "a dense display starts at 1x whatever the rates; just under the mark it is a plain one" {
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 2.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 144.0, fps2x = 144.0, density = 3.0) shouldBe 1
        PictureQuality.oversampleFor(fps1x = 60.0, fps2x = 60.0, density = 1.99) shouldBe 2
    }

    "the 2x part is run only on a plain display and where 1x is fast enough" {
        PictureQuality.measuresTwice(fps1x = 60.0, density = 1.0) shouldBe true
        PictureQuality.measuresTwice(fps1x = 55.0, density = 1.99) shouldBe true
        PictureQuality.measuresTwice(fps1x = 54.9, density = 1.0) shouldBe false
        PictureQuality.measuresTwice(fps1x = 60.0, density = 2.0) shouldBe false
    }
})
