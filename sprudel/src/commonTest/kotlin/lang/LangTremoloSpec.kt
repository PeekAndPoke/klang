/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangTremoloSpec : StringSpec({

    // -- tremolo(sync = ...) ----------------------------------------------------------------------------------------------------

    "tremolo(sync = ...) sets VoiceData.tremoloSync correctly" {
        val p = note("c3").tremolo(sync = "4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
    }

    "tremolo(sync = ...) works as top-level function" {
        val p = note("a").apply(tremolo(sync = "2.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 2.0
    }

    "tremolo(sync = ...) works with control pattern" {
        val p = note("c3 e3").tremolo(sync = "2.0 4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloSync shouldBe 2.0
        events[1].data.tremoloSync shouldBe 4.0
    }

    // -- tremolo(depth = ...) ---------------------------------------------------------------------------------------------------

    "tremolo(depth = ...) sets VoiceData.tremoloDepth correctly" {
        val p = note("c3").tremolo(depth = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
    }

    "tremolo(depth = ...) works with control pattern" {
        val p = note("c3 e3").tremolo(depth = "0.3 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloDepth shouldBe 0.3
        events[1].data.tremoloDepth shouldBe 0.7
    }

    // -- tremolo(skew = ...) ----------------------------------------------------------------------------------------------------

    "tremolo(skew = ...) sets VoiceData.tremoloSkew correctly" {
        val p = note("c3").tremolo(skew = "0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSkew shouldBe 0.6
    }

    "tremolo(skew = ...) works with control pattern" {
        val p = note("c3 e3").tremolo(skew = "0.2 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloSkew shouldBe 0.2
        events[1].data.tremoloSkew shouldBe 0.8
    }

    // -- tremolo(phase = ...) ---------------------------------------------------------------------------------------------------

    "tremolo(phase = ...) sets VoiceData.tremoloPhase correctly" {
        val p = note("c3").tremolo(phase = "0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloPhase shouldBe 0.25
    }

    "tremolo(phase = ...) works with control pattern" {
        val p = note("c3 e3").tremolo(phase = "0.0 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloPhase shouldBe 0.0
        events[1].data.tremoloPhase shouldBe 0.5
    }

    // -- tremolo(shape = ...) ---------------------------------------------------------------------------------------------------

    "tremolo(shape = ...) sets VoiceData.tremoloShape correctly" {
        val p = note("c3").tremolo(shape = "sine")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "sine"
    }

    "tremolo(shape = ...) works as top-level function" {
        val p = note("a").apply(tremolo(shape = "tri"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "tri"
    }

    "tremolo(shape = ...) works with control pattern (string sequence)" {
        val p = note("c3 e3").tremolo(shape = "sine square")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloShape shouldBe "sine"
        events[1].data.tremoloShape shouldBe "square"
    }

    "tremolo(shape = ...) converts to lowercase" {
        val p = note("c3").tremolo(shape = "SINE")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloShape shouldBe "sine"
    }

    // -- per-param tests --------------------------------------------------------------------------------

    "tremolo functions can be chained together" {
        val p = note("c3")
            .tremolo(sync = "4.0")
            .tremolo(depth = "0.5")
            .tremolo(skew = "0.6")
            .tremolo(phase = "0.25")
            .tremolo(shape = "sine")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSkew shouldBe 0.6
        events[0].data.tremoloPhase shouldBe 0.25
        events[0].data.tremoloShape shouldBe "sine"
    }

    "tremolo functions work in compiled code" {
        val p = SprudelPattern.compile("""note("c3").tremolo(sync = 4, depth = 0.5, shape = "sine")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloShape shouldBe "sine"
    }
})
