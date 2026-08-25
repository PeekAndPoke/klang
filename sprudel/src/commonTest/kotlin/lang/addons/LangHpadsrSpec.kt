/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangHpadsrSpec : StringSpec({

    "hpadsr dsl interface" {
        val pat = "c3"
        
        dslInterfaceTests(
            "pattern.hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).hpadsr(0.01, 0.3, 0.5, 0.5),
            "script pattern.hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").hpadsr(0.01, 0.3, 0.5, 0.5)"""),
            "string.hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    pat.hpadsr(0.01, 0.3, 0.5, 0.5),
            "script string.hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile(""""$pat".hpadsr(0.01, 0.3, 0.5, 0.5)"""),
            "hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).apply(hpadsr(0.01, 0.3, 0.5, 0.5)),
            "script hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(hpadsr(0.01, 0.3, 0.5, 0.5))"""),
            "chained hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).apply(hpadsr(0.01, 0.3, 0.5, 0.5).hpadsr(0.01, 0.3, 0.5, 0.5)),
            "script chained hpadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(hpadsr(0.01, 0.3, 0.5, 0.5).hpadsr(0.01, 0.3, 0.5, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.hpattack shouldBe 0.01
                events[0].data.hpdecay shouldBe 0.3
                events[0].data.hpsustain shouldBe 0.5
                events[0].data.hprelease shouldBe 0.5
            }
        }
    }

    "hpadsr() sets all four params" {
        val p = note("c3").hpadsr(0.02, 0.4, 0.6, 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.02
            hpdecay shouldBe 0.4
            hpsustain shouldBe 0.6
            hprelease shouldBe 0.8
        }
    }

    "hpadsr() with partial params sets only specified fields" {
        val p = note("c3").hpadsr(0.01, 0.3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.01
            hpdecay shouldBe 0.3
            hpsustain shouldBe null
            hprelease shouldBe null
        }
    }

    "hpadsr() works with control pattern" {
        val p = note("c3 e3").hpadsr("0.01 0.05", "0.2 0.4", "0.5 0.7", "0.3 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.2
        events[1].data.hpattack shouldBe 0.05
        events[1].data.hpdecay shouldBe 0.4
    }

    "hpadsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").hpadsr(0.01, 0.3, 0.5, 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.3
    }
})
