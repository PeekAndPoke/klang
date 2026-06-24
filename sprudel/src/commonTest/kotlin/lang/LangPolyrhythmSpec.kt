/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.CycleTime

class LangPolyrhythmSpec : StringSpec({

    "polyrhythm() aliases stack()" {
        // Just checking basic stack behavior since it's an alias
        val p = polyrhythm("a", "b")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        // Both play full cycle
        events.all { it.part.duration == CycleTime.ONE } shouldBe true
    }
})
