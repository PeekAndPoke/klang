/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangAdsrSpec : StringSpec({

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
})
