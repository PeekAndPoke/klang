/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangDistortSpec : StringSpec({

    // -- per-param (amount, shape, oversample) -----------------------------------------

    "distort() with amount only preserves backward compat" {
        val p = note("c").distort(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.7
        events[0].data.distortShape shouldBe null
    }

    // -- distort(shape = ...) --------------------------------------------------------------------------------------

    "distort(shape = ...) converts to lowercase" {
        val p = note("c").distort(shape = "FOLD")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortShape shouldBe "fold"
    }

    "distort(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the distort field
        val p = SprudelPattern.compile("""seq("3 4").distort(shape = "tube")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.distort shouldBe null
        events[0].data.distortShape shouldBe "tube"
    }
})
