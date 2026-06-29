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

class LangDetuneSpec : StringSpec({

    "detune dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.detune(ctrl)" to
                    seq(pat).detune(ctrl),
            "script pattern.detune(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").detune("$ctrl")"""),
            "string.detune(ctrl)" to
                    pat.detune(ctrl),
            "script string.detune(ctrl)" to
                    SprudelPattern.compile(""""$pat".detune("$ctrl")"""),
            "detune(ctrl)" to
                    seq(pat).apply(detune(ctrl)),
            "script detune(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(detune("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("detune") shouldBe 0.0
            events[1].data.oscParams?.get("detune") shouldBe 0.25
        }
    }

    "reinterpret voice data as detune | seq(\"0 1\").detune()" {
        val p = seq("0 1").detune()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("detune") shouldBe 0.0
            events[1].data.oscParams?.get("detune") shouldBe 1.0
        }
    }

    "reinterpret voice data as detune | \"0 1\".detune()" {
        val p = "0 1".detune()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("detune") shouldBe 0.0
            events[1].data.oscParams?.get("detune") shouldBe 1.0
        }
    }

    "reinterpret voice data as detune | seq(\"0 1\").apply(detune())" {
        val p = seq("0 1").apply(detune())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("detune") shouldBe 0.0
            events[1].data.oscParams?.get("detune") shouldBe 1.0
        }
    }

    "detune() sets VoiceData.detune" {
        val p = "0 1".apply(detune("0.1 0.2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("detune") } shouldBe listOf(0.1, 0.2)
    }

    "detune() works as pattern extension" {
        val p = note("c").detune("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("detune") shouldBe 0.1
    }

    "detune() works as string extension" {
        val p = "c".detune("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("detune") shouldBe 0.1
    }

    "detune() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").detune("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("detune") shouldBe 0.1
    }

    "detune() with continuous pattern sets detune correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").detune(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.oscParams?.get("detune") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.oscParams?.get("detune") shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.oscParams?.get("detune") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.oscParams?.get("detune") shouldBe (0.0 plusOrMinus EPSILON)
    }
})
