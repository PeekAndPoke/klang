/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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

class LangHpfSpec : StringSpec({

    // ---- hpf ----

    "hpf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.hpf(ctrl)" to seq(pat).hpf(ctrl),
            "script pattern.hpf(ctrl)" to SprudelPattern.compile("""seq("$pat").hpf("$ctrl")"""),
            "string.hpf(ctrl)" to pat.hpf(ctrl),
            "script string.hpf(ctrl)" to SprudelPattern.compile(""""$pat".hpf("$ctrl")"""),
            "hpf(ctrl)" to seq(pat).apply(hpf(ctrl)),
            "script hpf(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").hpf()" {
        val p = seq("1000 500").hpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | \"1000 500\".hpf()" {
        val p = "1000 500".hpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as hcutoff | seq(\"1000 500\").apply(hpf())" {
        val p = seq("1000 500").apply(hpf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hcutoff shouldBe 1000.0
            events[1].data.hcutoff shouldBe 500.0
        }
    }


    "hpf() works as pattern extension" {
        val p = note("c").hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works as string extension" {
        val p = "c".hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() with continuous pattern sets cutoffHz correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hcutoff shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hcutoff shouldBe (0.0 plusOrMinus EPSILON)
    }



    // ---- C0 guard: per-param (freq, q) ----

    "hpf(freq, q) sets both fields" {
        val p = note("c e").hpf("200 800", 1.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 200.0
        events[1].data.hcutoff shouldBe 800.0
        events[0].data.hresonance shouldBe 1.5
        events[1].data.hresonance shouldBe 1.5
    }

    "hpf(q = ...) does not clear a previously set freq" {
        val p = SprudelPattern.compile("""note("c3").hpf(800).hpf(q = 12)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
        events[0].data.hresonance shouldBe 12.0
    }
})
