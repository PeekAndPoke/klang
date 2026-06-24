/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.scale

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ScalesDataTest : StringSpec({
    "ScalesData should contain all expected scales" {
        // We can just verify that the data exists and has the expected size
        ScalesData.SCALES.size shouldBe 92
    }
})
