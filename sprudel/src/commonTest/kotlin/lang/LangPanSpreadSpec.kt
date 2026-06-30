/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
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
            "pattern.panSpread(ctrl)" to
                    seq(pat).panSpread(ctrl),
            "script pattern.panSpread(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").panSpread("$ctrl")"""),
            "string.panSpread(ctrl)" to
                    pat.panSpread(ctrl),
            "script string.panSpread(ctrl)" to
                    SprudelPattern.compile(""""$pat".panSpread("$ctrl")"""),
            "panSpread(ctrl)" to
                    seq(pat).apply(panSpread(ctrl)),
            "script spread(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(panSpread("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("panSpread") shouldBe 0.0
            events[1].data.oscParams?.get("panSpread") shouldBe 0.25
        }
    }

    "reinterpret voice data as panSpread | seq(\"0 1\").panSpread()" {
        val p = seq("0 1").panSpread()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("panSpread") shouldBe 0.0
            events[1].data.oscParams?.get("panSpread") shouldBe 1.0
        }
    }

    "reinterpret voice data as panSpread | \"0 1\".panSpread()" {
        val p = "0 1".panSpread()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("panSpread") shouldBe 0.0
            events[1].data.oscParams?.get("panSpread") shouldBe 1.0
        }
    }

    "reinterpret voice data as panSpread | seq(\"0 1\").apply(panSpread())" {
        val p = seq("0 1").apply(panSpread())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("panSpread") shouldBe 0.0
            events[1].data.oscParams?.get("panSpread") shouldBe 1.0
        }
    }

    "panSpread() sets VoiceData.panSpread" {
        val p = "0 1".apply(panSpread("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("panSpread") } shouldBe listOf(0.5, 1.0)
    }

    "panSpread() works as pattern extension" {
        val p = note("c").panSpread("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "panSpread() works as string extension" {
        val p = "c".panSpread("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "panSpread() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").panSpread("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.5
    }

    "panSpread() with continuous pattern sets panSpread correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").panSpread(sine)
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
