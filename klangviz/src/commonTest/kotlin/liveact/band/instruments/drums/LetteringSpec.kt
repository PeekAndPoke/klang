/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class LetteringSpec : StringSpec({

    "a name takes the room it is given, whatever its letters' widths: the gaps between them counted" {
        // two letters half a size wide each and one gap: 1.03 sizes wide in all
        nameSize(widths = listOf(0.5, 0.5), room = 103.0, most = 500.0) shouldBe (100.0 plusOrMinus 1e-9)
        // wider letters, as another machine's font has them: a smaller size, the same width
        val size = nameSize(widths = listOf(0.8, 0.9, 0.7), room = 300.0, most = 500.0)

        size * (0.8 + 0.9 + 0.7 + 2 * NAME_GAP) shouldBe (300.0 plusOrMinus 1e-9)
    }

    "a short name is not blown up to fill the room" {
        nameSize(widths = listOf(0.5), room = 300.0, most = 100.0) shouldBe 100.0
        nameSize(widths = listOf(0.5), room = 30.0, most = 100.0) shouldBe (60.0 plusOrMinus 1e-9)
    }

    "no letters, or letters without a width: no size" {
        nameSize(widths = emptyList(), room = 300.0, most = 100.0) shouldBe 0.0
        nameSize(widths = listOf(0.0, 0.0), room = 300.0, most = 100.0) shouldBe 0.0
    }
})
