/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangTremoloCompoundSpec : StringSpec({

    // -- rate only (the head since KLANG-00417 B2: rate first, as on the Ignitor door) ---------------------------------

    "tremolo(4) sets rate only" {
        val p = note("c3").tremolo(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloRate shouldBe 4.0
        events[0].data.tremoloDepth shouldBe null
        events[0].data.tremoloShape shouldBe null
    }

    "tremolo(4, 0.8) sets rate, then depth" {
        val events = note("c3").tremolo(4, 0.8).queryArc(0.0, 1.0)

        events[0].data.tremoloRate shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.8
    }

    "a bare tremolo() on a pattern with no numbers keeps the rate it had (the head's fallback, as vibrato's)" {
        val events = note("c3").tremolo(4).tremolo().queryArc(0.0, 1.0)

        events[0].data.tremoloRate shouldBe 4.0
    }

    // -- compiled scripts --------------------------------------------------------------------------------------------

    "tremolo(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the tremoloRate field
        val p = SprudelPattern.compile("""seq("3 4").tremolo(depth = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.tremoloRate shouldBe null
        events[0].data.tremoloDepth shouldBe 0.5
    }
})
