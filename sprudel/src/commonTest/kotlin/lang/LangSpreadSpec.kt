/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangSpreadSpec : StringSpec({

    "detune dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.unison(spread = ctrl)" to
                    seq(pat).unison(spread = ctrl),
            "script pattern.unison(spread = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").unison(spread = "$ctrl")"""),
            "string.unison(spread = ctrl)" to
                    pat.unison(spread = ctrl),
            "script string.unison(spread = ctrl)" to
                    SprudelPattern.compile(""""$pat".unison(spread = "$ctrl")"""),
            "unison(spread = ctrl)" to
                    seq(pat).apply(unison(spread = ctrl)),
            "script detune(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(unison(spread = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("spread") shouldBe 0.0
            events[1].data.oscParams?.get("spread") shouldBe 0.25
        }
    }

    "unison(spread = ...) sets VoiceData.spread" {
        val p = "0 1".apply(unison(spread = "0.1 0.2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("spread") } shouldBe listOf(0.1, 0.2)
    }

    "unison(spread = ...) works as pattern extension" {
        val p = note("c").unison(spread = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("spread") shouldBe 0.1
    }

    "unison(spread = ...) works as string extension" {
        val p = "c".unison(spread = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("spread") shouldBe 0.1
    }

    "unison(spread = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").unison(spread = "0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("spread") shouldBe 0.1
    }

    "unison(spread = ...) with continuous pattern sets spread correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").unison(spread = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.oscParams?.get("spread") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.oscParams?.get("spread") shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.oscParams?.get("spread") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.oscParams?.get("spread") shouldBe (0.0 plusOrMinus EPSILON)
    }
})
