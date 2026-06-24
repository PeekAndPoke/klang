/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GapPatternSpec : StringSpec({

    "GapPattern returns no events and preserves steps" {
        val pattern = GapPattern(3.0)
        pattern.numSteps shouldBe 3.0
        pattern.queryArc(0.0, 1.0).size shouldBe 0
    }
})
