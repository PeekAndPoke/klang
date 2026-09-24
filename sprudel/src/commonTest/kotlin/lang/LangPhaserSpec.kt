/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangPhaserSpec : StringSpec({

    // -- phaser() ---------------------------------------------------------------------------------------------------------

    // The first positional argument is the WET since step 3d(iii), 2026-09-24: wet first, as on
    // every door that has one.
    "phaser dsl interface" {
        dslInterfaceTests(
            "pattern.phaser(wet)" to note("c3").phaser("0.4"),
            "script pattern.phaser(wet)" to SprudelPattern.compile("""note("c3").phaser("0.4")"""),
            "string.phaser(wet)" to "c3".phaser("0.4"),
            "script string.phaser(wet)" to SprudelPattern.compile(""""c3".phaser("0.4")"""),
            "phaser(wet)" to note("c3").apply(phaser("0.4")),
            "script phaser(wet)" to SprudelPattern.compile("""note("c3").apply(phaser("0.4"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.phaserDepth shouldBe 0.4
            events[0].data.phaserRate shouldBe PHASER_RATE_HZ
        }
    }

    "phaser(rate = ...) sets VoiceData.phaserRate correctly" {
        val p = note("c3").phaser(rate = "2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 2.0
    }

    "phaser(rate = ...) works as top-level function" {
        val p = note("a").apply(phaser(rate = "1.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserRate shouldBe 1.5
    }

    "phaser(rate = ...) works with control pattern" {
        val p = note("c3 e3").phaser(rate = "1.0 2.0")
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

    // -- per-param, positional (wet, rate, center, sweep) ----------------------------------------

    "phaser() per-param sets all four VoiceData fields" {
        val p = note("c3").phaser(0.8, 2.0, 500, 1000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 2.0
            phaserDepth shouldBe 0.8
            phaserCenter shouldBe 500.0
            phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param fills the params it was not given with their shared constants" {
        // Katalyst step 5a-3: the phaser has no name knob, so any of its five names the stage and
        // the rest take their `PHASER_*` constants. Byte-identical, because `VoiceFactory`
        // substituted exactly those for a null field.
        val p = note("c3").phaser(0.6, 1.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 1.5
            phaserDepth shouldBe 0.6
            phaserCenter shouldBe PHASER_CENTER_HZ
            phaserSweep shouldBe PHASER_SWEEP_HZ
            phaserFloor shouldBe PHASER_FLOOR
        }
    }

    "phaser() per-param works as string extension" {
        val p = "c3".phaser(0.8, 2.0, 500, 1000)
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
        val p = SprudelPattern.compile("""note("c3").phaser(0.8, 2.0, 500, 1000)""")
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
        val p = note("c3 e3").phaser("<0.3 0.8>", "<0.5 2.0>", "<~ 500>", "<~ 1000>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.phaserRate shouldBe 0.5
            cycle0[0].data.phaserDepth shouldBe 0.3
            // A rest in the center pattern writes nothing, so the centre is the one the WET of the
            // same call filled in, not a value the rest produced.
            cycle0[0].data.phaserCenter shouldBe PHASER_CENTER_HZ

            cycle1.size shouldBe 2
            cycle1[0].data.phaserRate shouldBe 2.0
            cycle1[0].data.phaserDepth shouldBe 0.8
            cycle1[0].data.phaserCenter shouldBe 500.0
            cycle1[0].data.phaserSweep shouldBe 1000.0
        }
    }

    "phaser() per-param works chained with other effects" {
        val p = note("c3").apply(gain(0.8).phaser(0.6, 2.0, 500, 1000))
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
        val p = note("c3").phaser(0.5, 1.0, 300)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            phaserRate shouldBe 1.0
            phaserDepth shouldBe 0.5
            phaserCenter shouldBe 300.0
            phaserSweep shouldBe PHASER_SWEEP_HZ
        }
    }

    "phaser() with a single positional value sets the WET, and the wet names the stage" {
        val p = note("c3").phaser(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.phaserDepth shouldBe 0.7
        // The wet names the stage (the phaser has no name knob), so the four companions take their
        // constants. PHASER_RATE_HZ is 0, a phaser standing still until a rate arrives.
        events[0].data.phaserRate shouldBe PHASER_RATE_HZ
        events[0].data.phaserCenter shouldBe PHASER_CENTER_HZ
        events[0].data.phaserSweep shouldBe PHASER_SWEEP_HZ
    }

    "phaser(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the phaserDepth field, the WET being the head
        val p = SprudelPattern.compile("""seq("3 4").phaser(rate = 0.6)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        // The wet is the constant the rate's own fill wrote, never the receiver's 3 or 4: that is
        // what this row guards, and PHASER_WET is 0, the engine's gate.
        events.map { it.data.phaserDepth } shouldBe listOf(PHASER_WET, PHASER_WET)
        events.map { it.data.phaserRate } shouldBe listOf(0.6, 0.6)
    }

    "a bare phaser() reinterprets the pattern's own values as the WET, in both doors" {
        listOf(
            "kotlin" to seq("0.2 0.5").phaser(),
            "script" to SprudelPattern.compile("""seq("0.2 0.5").phaser()"""),
        ).forEach { (door, p) ->
            val events = p?.queryArc(0.0, 1.0) ?: emptyList()

            withClue(door) {
                events.map { it.data.phaserDepth } shouldBe listOf(0.2, 0.5)
                events.map { it.data.katalystParams?.get("phaser.wet") } shouldBe listOf(0.2, 0.5)
                // The wet names the stage, so the bare call fills like any other phaser call.
                events.map { it.data.phaserRate } shouldBe listOf(PHASER_RATE_HZ, PHASER_RATE_HZ)
            }
        }
    }
})
