/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.peekandpoke.klang.sprudel.EPSILON
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangHpqSpec : StringSpec({

    // ---- hpq ----


    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").hpq()" {
        val p = seq("0.5 1.0").hpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | \"0.5 1.0\".hpq()" {
        val p = "0.5 1.0".hpq()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "reinterpret voice data as hresonance | seq(\"0.5 1.0\").apply(hpq())" {
        val p = seq("0.5 1.0").apply(hpq())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }






    

    "hpq dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpq(ctrl)" to seq(pat).hpq(ctrl),
            "script pattern.hpq(ctrl)" to SprudelPattern.compile("""seq("$pat").hpq("$ctrl")"""),
            "string.hpq(ctrl)" to pat.hpq(ctrl),
            "script string.hpq(ctrl)" to SprudelPattern.compile(""""$pat".hpq("$ctrl")"""),
            "hpq(ctrl)" to seq(pat).apply(hpq(ctrl)),
            "script hpq(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }


    "hpq() with continuous pattern sets hresonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpq(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hresonance shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hresonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
