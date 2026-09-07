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

class LangHpadsrSpec : StringSpec({

    "hpadsr dsl interface" {
        val pat = "c3"
        
        dslInterfaceTests(
            "pattern.hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script pattern.hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "string.hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    pat.hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script string.hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile(""""$pat".hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).apply(hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)),
            "script hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5))"""),
            "chained hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).apply(hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5).hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)),
            "script chained hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5).hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5))"""),
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

    "hpf(attack = ...) sets all four params" {
        val p = note("c3").hpf(attack = 0.02, decay = 0.4, sustain = 0.6, release = 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.02
            hpdecay shouldBe 0.4
            hpsustain shouldBe 0.6
            hprelease shouldBe 0.8
        }
    }

    "hpf(attack = ...) with partial params sets only specified fields" {
        val p = note("c3").hpf(attack = 0.01, decay = 0.3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.01
            hpdecay shouldBe 0.3
            hpsustain shouldBe null
            hprelease shouldBe null
        }
    }

    "hpf(attack = ...) works with control pattern" {
        val p = note("c3 e3").hpf(attack = "0.01 0.05", decay = "0.2 0.4", sustain = "0.5 0.7", release = "0.3 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.2
        events[1].data.hpattack shouldBe 0.05
        events[1].data.hpdecay shouldBe 0.4
    }

    "hpf(attack = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").hpf(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.3
    }
})
