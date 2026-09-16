/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * `reverb(wet, size, lowpass)`, the orbit reverb. The size slot has its own
 * [LangReverbSizeSpec]. `reverb` replaced `room` on 2026-09-16 (`docs/tasks-archive/2026-09/20260916-reverb-naming-unification.md`).
 */
class LangReverbSpec : StringSpec({

    // -- reverb(wet) ------------------------------------------------------------------------------------------------------

    "reverb dsl interface" {
        dslInterfaceTests(
            "pattern.reverb(amount)" to note("c").reverb(0.5),
            "script pattern.reverb(amount)" to SprudelPattern.compile("""note("c").reverb(0.5)"""),
            "string.reverb(amount)" to "c".reverb(0.5),
            "script string.reverb(amount)" to SprudelPattern.compile(""""c".reverb(0.5)"""),
            "reverb(amount)" to note("c").apply(reverb(0.5)),
            "script reverb(amount)" to SprudelPattern.compile("""note("c").apply(reverb(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as the reverb send | seq(\"0 0.5\").reverb()" {
        val p = seq("0 0.5").reverb()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.reverb shouldBe 0.0
            events[1].data.reverb shouldBe 0.5
        }
    }

    "reinterpret voice data as the reverb send | \"0 0.5\".reverb()" {
        val p = "0 0.5".reverb()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.reverb shouldBe 0.0
            events[1].data.reverb shouldBe 0.5
        }
    }

    "reinterpret voice data as the reverb send | seq(\"0 0.5\").apply(reverb())" {
        val p = seq("0 0.5").apply(reverb())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.reverb shouldBe 0.0
            events[1].data.reverb shouldBe 0.5
        }
    }

    "reverb() sets VoiceData.reverb" {
        val p = note("a b").reverb("0.5 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.reverb } shouldBe listOf(0.5, 0.8)
    }

    "reverb() works as string extension" {
        val p = "c".reverb("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.reverb shouldBe 0.5
    }

    "reverb() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").reverb("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.reverb shouldBe 0.5
    }

    "reverb() with a continuous pattern sets the send" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").reverb(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.reverb shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.reverb shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.reverb shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.reverb shouldBe (0.0 plusOrMinus EPSILON)
    }

    // -- positional slots -------------------------------------------------------------------------------------------------

    "reverb(wet, size, lowpass) sets all three slots, in that order" {
        val p = note("c").reverb(0.5, 2, 4000)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.reverb shouldBe 0.5
            events[0].data.reverbSize shouldBe 2.0
            events[0].data.reverbLowpass shouldBe 4000.0
        }
    }

    "the third positional slot is lowpass in compiled code too" {
        // `room(wet, size, fade)` had fade third; a migrated positional call must not silently
        // land a fade value in the lowpass.
        val p = SprudelPattern.compile("""note("c").reverb(0.8, 2, 3000)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        assertSoftly {
            events[0].data.reverb shouldBe 0.8
            events[0].data.reverbSize shouldBe 2.0
            events[0].data.reverbLowpass shouldBe 3000.0
        }
    }

    "reverb() with leading params sets only wet and size" {
        val p = note("c").reverb(0.8, 4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        assertSoftly {
            events[0].data.reverb shouldBe 0.8
            events[0].data.reverbSize shouldBe 4.0
            events[0].data.reverbLowpass shouldBe null
        }
    }

    "reverb() with a single param sets only the send" {
        val p = note("c").reverb(0.6)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            reverb shouldBe (0.6 plusOrMinus EPSILON)
            reverbSize shouldBe null
            reverbLowpass shouldBe null
        }
    }

    "reverb() with per-param sequenced values" {
        val p = note("c c").reverb("0.3 0.8", "1 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        assertSoftly {
            events[0].data.reverb shouldBe 0.3
            events[0].data.reverbSize shouldBe 1.0
            events[1].data.reverb shouldBe 0.8
            events[1].data.reverbSize shouldBe 4.0
        }
    }

    "reverb() works with mini-notation patterns" {
        val p = note("c3 e3").reverb("<0.3 0.8>", "<1 4>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.reverb shouldBe 0.3
            cycle0[0].data.reverbSize shouldBe 1.0

            cycle1.size shouldBe 2
            cycle1[0].data.reverb shouldBe 0.8
            cycle1[0].data.reverbSize shouldBe 4.0
        }
    }

    "reverb() works chained with other effects" {
        val p = note("c").apply(gain(0.8).reverb(0.5, 2))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            reverb shouldBe 0.5
            reverbSize shouldBe 2.0
        }
    }

    "reverb(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the send
        val p = SprudelPattern.compile("""seq("3 4").reverb(size = 8)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.reverb shouldBe null
        events[0].data.reverbSize shouldBe 8.0
    }

    // -- reverb(lowpass = ...) --------------------------------------------------------------------------------------------

    "reverb(lowpass = ...) sets VoiceData.reverbLowpass correctly" {
        val p = note("c3").reverb(lowpass = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.reverbLowpass shouldBe 1000.0
    }

    "reverb(lowpass = ...) works as top-level function" {
        val p = note("a").apply(reverb(lowpass = "500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.reverbLowpass shouldBe 500.0
    }

    "reverb(lowpass = ...) works with control pattern" {
        val p = note("c3 e3").reverb(lowpass = "800 1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.reverbLowpass shouldBe 800.0
        events[1].data.reverbLowpass shouldBe 1200.0
    }

    "reverb(lowpass = ...) works as string extension" {
        val p = "c3".reverb(lowpass = "1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.reverbLowpass shouldBe 1500.0
    }

    // -- chaining ---------------------------------------------------------------------------------------------------------

    "reverb slots can be chained together" {
        val p = note("c3")
            .reverb("0.8")
            .reverb(size = "0.9")
            .reverb(lowpass = "1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.reverb shouldBe 0.8
        events[0].data.reverbSize shouldBe 0.9
        events[0].data.reverbLowpass shouldBe 1000.0
    }

    "reverb slots work named in compiled code" {
        val p = SprudelPattern.compile("""note("c3").reverb(wet = 0.8, size = 4, lowpass = 1000)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.reverb shouldBe 0.8
        events[0].data.reverbSize shouldBe 4.0
        events[0].data.reverbLowpass shouldBe 1000.0
    }
})
