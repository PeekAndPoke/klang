/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangAdsrSpec : StringSpec({

    "adsr dsl interface" {
        val pat = "0 1"

        dslInterfaceTests(
            "pattern.adsr(a, d, s, r)" to
                    seq(pat).adsr(0.1, 0.2, 0.8, 0.5),
            "script pattern.adsr(a, d, s, r)" to
                    SprudelPattern.compile("""seq("$pat").adsr(0.1, 0.2, 0.8, 0.5)"""),
            "string.adsr(a, d, s, r)" to
                    pat.adsr(0.1, 0.2, 0.8, 0.5),
            "script string.adsr(a, d, s, r)" to
                    SprudelPattern.compile(""""$pat".adsr(0.1, 0.2, 0.8, 0.5)"""),
            "adsr(a, d, s, r)" to
                    seq(pat).apply(adsr(0.1, 0.2, 0.8, 0.5)),
            "script adsr(a, d, s, r)" to
                    SprudelPattern.compile("""seq("$pat").apply(adsr(0.1, 0.2, 0.8, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.attack shouldBe 0.1
                events[0].data.decay shouldBe 0.2
                events[0].data.sustain shouldBe 0.8
                events[0].data.release shouldBe 0.5
            }
        }
    }

    "adsr() sets VoiceData ADSR components correctly via mapper" {
        val p = "0 1".apply(adsr(0.1, 0.2, 0.8, 0.5))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe 0.8
            release shouldBe 0.5
        }
    }

    "adsr() with leading params only leaves the rest unset" {
        val p = note("c").adsr(0.1, 0.2)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe null
            release shouldBe null
        }
    }

    "adsr() with named params skips unset stages" {
        val p = SprudelPattern.compile("""note("c").adsr(sustain = 0.8, release = 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe null
            decay shouldBe null
            sustain shouldBe 0.8
            release shouldBe 0.5
        }
    }

    "adsr() params are independently patternable" {
        // attack follows the sequence per event, release stays constant
        val p = note("c e").adsr("0.1 0.3", release = 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.attack shouldBe 0.1
        events[1].data.attack shouldBe 0.3
        events[0].data.release shouldBe 0.5
        events[1].data.release shouldBe 0.5
    }

    "adsr() alternation form selects per cycle" {
        val p = note("c").adsr("<0.1 0.3>")
        val c0 = p.queryArc(0.0, 1.0)
        val c1 = p.queryArc(1.0, 2.0)

        c0.size shouldBe 1
        c1.size shouldBe 1
        c0[0].data.attack shouldBe 0.1
        c1[0].data.attack shouldBe 0.3
    }

    "adsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").adsr(0.1, 0.2, 0.8, 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe 0.8
            release shouldBe 0.5
        }
    }
})
