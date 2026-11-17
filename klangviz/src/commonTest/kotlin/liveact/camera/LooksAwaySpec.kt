/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The total looks from the audience at the stage: along -z.

class LooksAwaySpec : StringSpec({

    "a look toward the stage, straight or from the side, does not look away" {
        looksAway(aheadX = 0.0, aheadZ = -9.4, totalX = 0.0, totalZ = -9.4) shouldBe false
        // from the left, low: the camera at (-7.2, 5.6) looks at (0, 0.6)
        looksAway(aheadX = 7.2, aheadZ = -5.0, totalX = 0.0, totalZ = -9.4) shouldBe false
        looksAway(aheadX = -3.0, aheadZ = -0.1, totalX = 0.0, totalZ = -9.4) shouldBe false
    }

    "the reverse total and the look over the drummer's shoulder look away" {
        // the reverse total: from (1.1, -4.6) at (-0.8, 12)
        looksAway(aheadX = -1.9, aheadZ = 16.6, totalX = 0.0, totalZ = -9.4) shouldBe true
        // over the drummer's shoulder, down on the kit
        looksAway(aheadX = 1.1, aheadZ = 2.2, totalX = 0.0, totalZ = -9.4) shouldBe true
    }

    "a look straight across the stage counts as away" {
        looksAway(aheadX = 5.0, aheadZ = 0.0, totalX = 0.0, totalZ = -9.4) shouldBe true
    }

    "it goes by the total's direction, wherever that points" {
        looksAway(aheadX = 1.0, aheadZ = 0.0, totalX = 1.0, totalZ = 0.0) shouldBe false
        looksAway(aheadX = -1.0, aheadZ = 0.0, totalX = 1.0, totalZ = 0.0) shouldBe true
    }
})
