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

class LangNfadsrSpec : StringSpec({

    "nfadsr dsl interface" {
        val pat = "c3"
        
        dslInterfaceTests(
            "pattern.notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script pattern.notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "string.notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    pat.notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5),
            "script string.notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile(""""$pat".notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)"""),
            "notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).apply(notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)),
            "script notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5))"""),
            "chained notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    note(pat).apply(notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5).notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)),
            "script chained notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5).notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.nfattack shouldBe 0.01
                events[0].data.nfdecay shouldBe 0.3
                events[0].data.nfsustain shouldBe 0.5
                events[0].data.nfrelease shouldBe 0.5
            }
        }
    }

    "notch(attack = ...) sets all four params" {
        val p = note("c3").notch(attack = 0.02, decay = 0.4, sustain = 0.6, release = 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            nfattack shouldBe 0.02
            nfdecay shouldBe 0.4
            nfsustain shouldBe 0.6
            nfrelease shouldBe 0.8
        }
    }

    "notch(attack = ...) with partial params sets only specified fields" {
        val p = note("c3").notch(attack = 0.01, decay = 0.3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            nfattack shouldBe 0.01
            nfdecay shouldBe 0.3
            nfsustain shouldBe null
            nfrelease shouldBe null
        }
    }

    "notch(attack = ...) works with control pattern" {
        val p = note("c3 e3").notch(attack = "0.01 0.05", decay = "0.2 0.4", sustain = "0.5 0.7", release = "0.3 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfattack shouldBe 0.01
        events[0].data.nfdecay shouldBe 0.2
        events[1].data.nfattack shouldBe 0.05
        events[1].data.nfdecay shouldBe 0.4
    }

    "notch(attack = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").notch(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.01
        events[0].data.nfdecay shouldBe 0.3
    }
})
