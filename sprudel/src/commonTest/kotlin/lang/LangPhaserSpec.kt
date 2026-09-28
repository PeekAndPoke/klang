/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangPhaserSpec : StringSpec({

    // -- the positional wet first, and the fill it triggers (step 3d(iii), 2026-09-24) ----------

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
