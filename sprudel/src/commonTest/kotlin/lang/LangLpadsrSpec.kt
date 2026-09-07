/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLpadsrSpec : StringSpec({

    "lpadsr dsl interface" {
        val pat = "c3"
        
        dslInterfaceTests(
            "pattern.lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script pattern.lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "string.lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    pat.lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script string.lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile(""""$pat".lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).apply(lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)),
            "script lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.lpattack shouldBe 0.01
                events[0].data.lpdecay shouldBe 0.3
                events[0].data.lpsustain shouldBe 0.5
                events[0].data.lprelease shouldBe 0.5
            }
        }
    }

    "lpf(attack = ...) sets all four params" {
        val p = note("c3").lpf(attack = 0.02, decay = 0.4, sustain = 0.6, release = 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            lpattack shouldBe 0.02
            lpdecay shouldBe 0.4
            lpsustain shouldBe 0.6
            lprelease shouldBe 0.8
        }
    }

    "lpf(attack = ...) with partial params sets only specified fields" {
        val p = note("c3").lpf(attack = 0.01, decay = 0.3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            lpattack shouldBe 0.01
            lpdecay shouldBe 0.3
            lpsustain shouldBe null
            lprelease shouldBe null
        }
    }

    "lpf(attack = ...) works with control pattern" {
        val p = note("c3 e3").lpf(attack = "0.01 0.05", decay = "0.2 0.4", sustain = "0.5 0.7", release = "0.3 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpattack shouldBe 0.01
        events[0].data.lpdecay shouldBe 0.2
        events[1].data.lpattack shouldBe 0.05
        events[1].data.lpdecay shouldBe 0.4
    }

    "lpf(attack = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").lpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.01
        events[0].data.lpdecay shouldBe 0.3
    }
})
