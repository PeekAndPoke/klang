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

class LangLpqSpec : StringSpec({

    // ---- resonance ----

    "lpf(q = ...) updates existing filters" {
        // Apply LPF first (default Q=1.0), then update resonance to 5.0
        val p = note("c").lpf(freq = "1000", q = "5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "lpf(q = ...) works as string extension" {
        val p = "c".lpf(q = "5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "lpf(q = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpf(q = "5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "lpq dsl interface" {
        val pat = "a b"
        val ctrl = "5 10"

        dslInterfaceTests(
            "pattern.lpf(q = ctrl)" to seq(pat).lpf(q = ctrl),
            "script pattern.lpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").lpf(q = "$ctrl")"""),
            "string.lpf(q = ctrl)" to pat.lpf(q = ctrl),
            "script string.lpf(q = ctrl)" to SprudelPattern.compile(""""$pat".lpf(q = "$ctrl")"""),
            "lpf(q = ctrl)" to seq(pat).apply(lpf(q = ctrl)),
            "script lpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpf(q = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.resonance shouldBe 5.0
            events[1].data.resonance shouldBe 10.0
        }
    }

    "lpf(q = ...) works as pattern extension" {
        val p = note("c").lpf(q = "8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpf(q = ...) with continuous pattern sets resonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpf(q = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.resonance shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.resonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
