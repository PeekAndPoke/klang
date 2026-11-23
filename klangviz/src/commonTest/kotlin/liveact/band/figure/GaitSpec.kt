/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan

class GaitSpec : StringSpec({

    "for the same gait, the catwalk moves the hips more than the stroll" {
        for (hips in listOf(0.2, 0.45, 1.0)) {
            val gait = Gait(hips = hips)

            gait.hipsOn(WalkKind.Camera) shouldBeGreaterThan gait.hipsOn(WalkKind.Stroll)
        }
    }
})
