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

class LangPanSpreadSpec : StringSpec({

    "spread dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.unison(pan = ctrl)" to
                    seq(pat).unison(pan = ctrl),
            "script pattern.unison(pan = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").unison(pan = "$ctrl")"""),
            "string.unison(pan = ctrl)" to
                    pat.unison(pan = ctrl),
            "script string.unison(pan = ctrl)" to
                    SprudelPattern.compile(""""$pat".unison(pan = "$ctrl")"""),
            "unison(pan = ctrl)" to
                    seq(pat).apply(unison(pan = ctrl)),
            "script unison(spread = ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(unison(pan = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("panSpread") shouldBe 0.0
            events[1].data.oscParams?.get("panSpread") shouldBe 0.25
        }
    }

    "unison(pan = ...) sets VoiceData.panSpread" {
        val p = "0 1".apply(unison(pan = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("panSpread") } shouldBe listOf(0.5, 1.0)
    }

    "unison(pan = ...) works as pattern extension" {
        val p = note("c").unison(pan = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "unison(pan = ...) works as string extension" {
        val p = "c".unison(pan = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "unison(pan = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").unison(pan = "0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "unison(pan = ...) with continuous pattern sets panSpread correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").unison(pan = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.oscParams?.get("panSpread") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.oscParams?.get("panSpread") shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.oscParams?.get("panSpread") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.oscParams?.get("panSpread") shouldBe (0.0 plusOrMinus EPSILON)
    }
})
