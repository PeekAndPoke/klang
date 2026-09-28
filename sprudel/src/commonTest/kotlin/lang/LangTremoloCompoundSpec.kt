/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangTremoloCompoundSpec : StringSpec({

    // -- depth only --------------------------------------------------------------------------------------------------

    "tremolo(0.8) sets depth only" {
        val p = note("c3").tremolo(0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.8
        events[0].data.tremoloSync shouldBe null
        events[0].data.tremoloShape shouldBe null
    }

    // -- compiled scripts --------------------------------------------------------------------------------------------

    "tremolo(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the tremoloDepth field
        val p = SprudelPattern.compile("""seq("3 4").tremolo(sync = 4)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.tremoloDepth shouldBe null
        events[0].data.tremoloSync shouldBe 4.0
    }
})
