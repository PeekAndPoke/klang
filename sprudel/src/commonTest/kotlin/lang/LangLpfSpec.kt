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

class LangLpfSpec : StringSpec({

    // ---- lpf ----

    "lpf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.lpf(ctrl)" to seq(pat).lpf(ctrl),
            "script pattern.lpf(ctrl)" to SprudelPattern.compile("""seq("$pat").lpf("$ctrl")"""),
            "string.lpf(ctrl)" to pat.lpf(ctrl),
            "script string.lpf(ctrl)" to SprudelPattern.compile(""""$pat".lpf("$ctrl")"""),
            "lpf(ctrl)" to seq(pat).apply(lpf(ctrl)),
            "script lpf(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").lpf()" {
        val p = seq("1000 500").lpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | \"1000 500\".lpf()" {
        val p = "1000 500".lpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }

    "reinterpret voice data as cutoff | seq(\"1000 500\").apply(lpf())" {
        val p = seq("1000 500").apply(lpf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.cutoff shouldBe 1000.0
            events[1].data.cutoff shouldBe 500.0
        }
    }


    "lpf() works as pattern extension" {
        val p = note("c").lpf("1000")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works as string extension" {
        val p = "c".lpf("1000")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }
})
