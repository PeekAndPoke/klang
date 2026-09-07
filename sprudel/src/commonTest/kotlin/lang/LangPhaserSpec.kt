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

class LangPhaserSpec : StringSpec({

    // -- phaser() ---------------------------------------------------------------------------------------------------------

    "phaser dsl interface" {
        dslInterfaceTests(
            "pattern.phaser(rate)" to note("c3").phaser("2.0"),
            "script pattern.phaser(rate)" to SprudelPattern.compile("""note("c3").phaser("2.0")"""),
            "string.phaser(rate)" to "c3".phaser("2.0"),
            "script string.phaser(rate)" to SprudelPattern.compile(""""c3".phaser("2.0")"""),
            "phaser(rate)" to note("c3").apply(phaser("2.0")),
            "script phaser(rate)" to SprudelPattern.compile("""note("c3").apply(phaser("2.0"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.phaserRate shouldBe 2.0
        }
    }

    "phaser() sets VoiceData.phaser correctly" {
        val p = note("c3").phaser("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 2.0
    }

    "phaser() works as top-level function" {
        val p = note("a").apply(phaser("1.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 1.5
    }

    "phaser() works with control pattern" {
        val p = note("c3 e3").phaser("1.0 2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserRate shouldBe 1.0
        events[1].data.phaserRate shouldBe 2.0
    }

    // -- phaser(wet = ...) ----------------------------------------------------------------------------------------------------

    "phaser(wet = ...) sets VoiceData.phaserDepth correctly" {
        val p = note("c3").phaser(wet = "0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserDepth shouldBe 0.8
    }

    "phaser(wet = ...) works with control pattern" {
        val p = note("c3 e3").phaser(wet = "0.3 0.9")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserDepth shouldBe 0.3
        events[1].data.phaserDepth shouldBe 0.9
    }

    // -- phaser(center = ...) ---------------------------------------------------------------------------------------------------

    "phaser(center = ...) sets VoiceData.phaserCenter correctly" {
        val p = note("c3").phaser(center = "500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserCenter shouldBe 500.0
    }

    "phaser(center = ...) works with control pattern" {
        val p = note("c3 e3").phaser(center = "300 700")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserCenter shouldBe 300.0
        events[1].data.phaserCenter shouldBe 700.0
    }

    // -- phaser(sweep = ...) ----------------------------------------------------------------------------------------------------

    "phaser(sweep = ...) sets VoiceData.phaserSweep correctly" {
        val p = note("c3").phaser(sweep = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserSweep shouldBe 1000.0
    }

    "phaser(sweep = ...) works with control pattern" {
        val p = note("c3 e3").phaser(sweep = "500 1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.phaserSweep shouldBe 500.0
        events[1].data.phaserSweep shouldBe 1500.0
    }

    // -- chaining tests ---------------------------------------------------------------------------------------------------

    "phaser functions can be chained together" {
        val p = note("c3").phaser(rate = "2.0", wet = "0.8", center = "500", sweep = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 2.0
        events[0].data.phaserDepth shouldBe 0.8
        events[0].data.phaserCenter shouldBe 500.0
        events[0].data.phaserSweep shouldBe 1000.0
    }

    "phaser functions work in compiled code" {
        val p = SprudelPattern.compile("""note("c3").phaser(rate = 2, wet = 0.8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 2.0
        events[0].data.phaserDepth shouldBe 0.8
    }

    // -- per-param (rate, depth, center, sweep) ----------------------------------------

    "phaser() per-param sets all four VoiceData fields" {
        val p = note("c3").phaser(2.0, 0.8, 500, 1000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 2.0
            phaserDepth shouldBe 0.8
            phaserCenter shouldBe 500.0
            phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param with partial params sets only specified fields" {
        val p = note("c3").phaser(1.5, 0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 1.5
            phaserDepth shouldBe 0.6
            phaserCenter shouldBe null
            phaserSweep shouldBe null
        }
    }

    "phaser() per-param works as string extension" {
        val p = "c3".phaser(2.0, 0.8, 500, 1000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 2.0
            phaserDepth shouldBe 0.8
            phaserCenter shouldBe 500.0
            phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").phaser(2.0, 0.8, 500, 1000)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 2.0
            phaserDepth shouldBe 0.8
            phaserCenter shouldBe 500.0
            phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param mini-notation patterns" {
        val p = note("c3 e3").phaser("<0.5 2.0>", "<0.3 0.8>", "<~ 500>", "<~ 1000>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.phaserRate shouldBe 0.5
            cycle0[0].data.phaserDepth shouldBe 0.3
            cycle0[0].data.phaserCenter shouldBe null    // rest in the center pattern leaves it unset

            cycle1.size shouldBe 2
            cycle1[0].data.phaserRate shouldBe 2.0
            cycle1[0].data.phaserDepth shouldBe 0.8
            cycle1[0].data.phaserCenter shouldBe 500.0
            cycle1[0].data.phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param works chained with other effects" {
        val p = note("c3").apply(gain(0.8).phaser(2.0, 0.6, 500, 1000))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            phaserRate shouldBe 2.0
            phaserDepth shouldBe 0.6
            phaserCenter shouldBe 500.0
            phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param works" {
        val p = note("c3").phaser(1.0, 0.5, 300)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 1.0
            phaserDepth shouldBe 0.5
            phaserCenter shouldBe 300.0
            phaserSweep shouldBe null
        }
    }

    "phaser() single value still works (backward compat)" {
        val p = note("c3").phaser(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 0.7
        events[0].data.phaserDepth shouldBe null
        events[0].data.phaserCenter shouldBe null
        events[0].data.phaserSweep shouldBe null
    }

    "phaser(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the phaserRate field
        val p = SprudelPattern.compile("""seq("3 4").phaser(wet = 0.6)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.phaserRate shouldBe null
        events[0].data.phaserDepth shouldBe 0.6
    }
})
