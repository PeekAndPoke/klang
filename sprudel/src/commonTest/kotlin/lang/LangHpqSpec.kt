/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.peekandpoke.klang.sprudel.EPSILON
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangHpqSpec : StringSpec({

    // ---- hpq ----

    

    "hpq dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpf(q = ctrl)" to seq(pat).hpf(q = ctrl),
            "script pattern.hpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").hpf(q = "$ctrl")"""),
            "string.hpf(q = ctrl)" to pat.hpf(q = ctrl),
            "script string.hpf(q = ctrl)" to SprudelPattern.compile(""""$pat".hpf(q = "$ctrl")"""),
            "hpf(q = ctrl)" to seq(pat).apply(hpf(q = ctrl)),
            "script hpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpf(q = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hresonance shouldBe 0.5
            events[1].data.hresonance shouldBe 1.0
        }
    }

    "hpf(q = ...) with continuous pattern sets hresonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpf(q = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hresonance shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hresonance shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hresonance shouldBe (0.0 plusOrMinus EPSILON)
    }
})
