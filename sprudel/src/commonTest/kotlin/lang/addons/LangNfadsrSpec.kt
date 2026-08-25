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
            "pattern.nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).nfadsr(0.01, 0.3, 0.5, 0.5),
            "script pattern.nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").nfadsr(0.01, 0.3, 0.5, 0.5)"""),
            "string.nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    pat.nfadsr(0.01, 0.3, 0.5, 0.5),
            "script string.nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile(""""$pat".nfadsr(0.01, 0.3, 0.5, 0.5)"""),
            "nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).apply(nfadsr(0.01, 0.3, 0.5, 0.5)),
            "script nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(nfadsr(0.01, 0.3, 0.5, 0.5))"""),
            "chained nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    note(pat).apply(nfadsr(0.01, 0.3, 0.5, 0.5).nfadsr(0.01, 0.3, 0.5, 0.5)),
            "script chained nfadsr(0.01, 0.3, 0.5, 0.5)" to
                    SprudelPattern.compile("""note("$pat").apply(nfadsr(0.01, 0.3, 0.5, 0.5).nfadsr(0.01, 0.3, 0.5, 0.5))"""),
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

    "nfadsr() sets all four params" {
        val p = note("c3").nfadsr(0.02, 0.4, 0.6, 0.8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            nfattack shouldBe 0.02
            nfdecay shouldBe 0.4
            nfsustain shouldBe 0.6
            nfrelease shouldBe 0.8
        }
    }

    "nfadsr() with partial params sets only specified fields" {
        val p = note("c3").nfadsr(0.01, 0.3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            nfattack shouldBe 0.01
            nfdecay shouldBe 0.3
            nfsustain shouldBe null
            nfrelease shouldBe null
        }
    }

    "nfadsr() works with control pattern" {
        val p = note("c3 e3").nfadsr("0.01 0.05", "0.2 0.4", "0.5 0.7", "0.3 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfattack shouldBe 0.01
        events[0].data.nfdecay shouldBe 0.2
        events[1].data.nfattack shouldBe 0.05
        events[1].data.nfdecay shouldBe 0.4
    }

    "nfadsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").nfadsr(0.01, 0.3, 0.5, 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.01
        events[0].data.nfdecay shouldBe 0.3
    }
})
