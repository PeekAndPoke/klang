/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FrameStatsSpec : StringSpec({

    "the frame rate comes from the median frame, so a few slow frames do not decide it" {
        val stats = FrameStats.of(listOf(20.0, 20.0, 150.0, 20.0, 25.0))

        stats.fps shouldBe 50.0
        stats.slowestMs shouldBe 150.0
    }

    "a frame longer than eight medians is an outlier: left out of the slowest" {
        // the median is 20 ms: 160 is the longest that still counts, 400 and a hundred seconds do not
        FrameStats.of(listOf(20.0, 20.0, 400.0, 20.0, 160.0, 100_000.0, 25.0)).slowestMs shouldBe 160.0
        FrameStats.of(listOf(20.0, 20.0, 400.0, 20.0, 25.0)).slowestMs shouldBe 25.0
        FrameStats.of(listOf(20.0, 20.0, 160.1, 20.0, 25.0)).slowestMs shouldBe 25.0
        FrameStats.of(listOf(20.0)).slowestMs shouldBe 20.0
    }

    "an even number of frames takes the middle two" {
        FrameStats.of(listOf(10.0, 30.0, 20.0, 40.0)).fps shouldBe 40.0
    }

    "no frames, or only what is no frame time, is nothing drawn" {
        FrameStats.of(emptyList()) shouldBe FrameStats.none
        FrameStats.of(listOf(Double.NaN, 0.0, -3.0)) shouldBe FrameStats.none
        FrameStats.of(listOf(Double.NaN, 25.0)) shouldBe FrameStats(fps = 40.0, slowestMs = 25.0)
    }
})
