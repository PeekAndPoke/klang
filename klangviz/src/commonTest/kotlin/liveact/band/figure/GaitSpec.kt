/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class GaitSpec : StringSpec({

    "on a walk the hips move as much as the member's own times the kind's" {
        for (hips in listOf(0.2, 0.45, 1.0)) {
            val gait = Gait(hips = hips)

            for (kind in WalkKind.entries) {
                withClue("a gait of $hips on $kind") {
                    gait.hipsOn(kind) shouldBe (hips * kind.hips plusOrMinus 1e-12)
                }
            }

            gait.hipsWith(1.25) shouldBe (hips * 1.25 plusOrMinus 1e-12)
        }

        Gait(hips = 0.2).hipsOn(WalkKind.WalkIn) shouldBe (0.14 plusOrMinus 1e-12)
        Gait(hips = 1.0).hipsOn(WalkKind.Camera) shouldBe (1.4 plusOrMinus 1e-12)
    }

    "a plain stroll is the measure; the catwalk moves the hips the most, the walk-in the least" {
        // the maintainer, 2026-11-20: a default per member, "and then an additional modifier depending on the 'kind of
        // walk'"; the plain stroll is how Sirene's hips moved before there were gaits
        WalkKind.Stroll.hips shouldBe 1.0
        WalkKind.entries.maxBy { it.hips } shouldBe WalkKind.Camera
        WalkKind.entries.minBy { it.hips } shouldBe WalkKind.WalkIn
    }
})
