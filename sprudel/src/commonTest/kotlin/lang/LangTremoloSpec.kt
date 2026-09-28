/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangTremoloSpec : StringSpec({

    // -- tremolo(shape = ...) ---------------------------------------------------------------------------------------------------

    "tremolo(shape = ...) converts to lowercase" {
        val p = note("c3").tremolo(shape = "SINE")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "sine"
    }
})
