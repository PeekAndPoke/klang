/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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

class LangLpqSpec : StringSpec({

    // ---- resonance ----


    "reinterpret voice data as resonance | seq(\"5 10\").lpq()" {
        val p = seq("5 10").lpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | \"5 10\".lpq()" {
        val p = "5 10".lpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "reinterpret voice data as resonance | seq(\"5 10\").apply(lpq())" {
        val p = seq("5 10").apply(lpq())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }



    "lpq() updates existing filters" {
        // Apply LPF first (default Q=1.0), then update resonance to 5.0
        val p = note("c").lpf("1000").lpq("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "lpq() works as string extension" {
        val p = "c".lpq("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "lpq() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpq("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }


    "lpq dsl interface" {
        val pat = "a b"
        val ctrl = "5 10"

        dslInterfaceTests(
            "pattern.lpq(ctrl)" to seq(pat).lpq(ctrl),
            "script pattern.lpq(ctrl)" to SprudelPattern.compile("""seq("$pat").lpq("$ctrl")"""),
            "string.lpq(ctrl)" to pat.lpq(ctrl),
            "script string.lpq(ctrl)" to SprudelPattern.compile(""""$pat".lpq("$ctrl")"""),
            "lpq(ctrl)" to seq(pat).apply(lpq(ctrl)),
            "script lpq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "lpq() works as pattern extension" {
        val p = note("c").lpq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpq() with continuous pattern sets resonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpq(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.resonance shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.resonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
