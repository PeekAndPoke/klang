/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
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
