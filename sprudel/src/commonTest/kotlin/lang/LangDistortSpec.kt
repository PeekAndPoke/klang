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

class LangDistortSpec : StringSpec({

    "distort dsl interface" {
        dslInterfaceTests(
            "pattern.distort(amount)" to note("c").distort(0.5),
            "script pattern.distort(amount)" to SprudelPattern.compile("""note("c").distort(0.5)"""),
            "string.distort(amount)" to "c".distort(0.5),
            "script string.distort(amount)" to SprudelPattern.compile(""""c".distort(0.5)"""),
            "distort(amount)" to note("c").apply(distort(0.5)),
            "script distort(amount)" to SprudelPattern.compile("""note("c").apply(distort(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as distort | seq(\"0 0.5\").distort()" {
        val p = seq("0 0.5").distort()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "reinterpret voice data as distort | \"0 0.5\".distort()" {
        val p = "0 0.5".distort()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "reinterpret voice data as distort | seq(\"0 0.5\").apply(distort())" {
        val p = seq("0 0.5").apply(distort())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "distort() sets VoiceData.distort" {
        val p = note("a b").distort("0.5 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.5, 10.0)
    }

    "distort() works as pattern extension" {
        val p = note("c").distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works as string extension" {
        val p = "c".distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() with continuous pattern sets distort correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").distort(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.distort shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.distort shouldBe (0.0 plusOrMinus EPSILON)
    }

    // Alias tests

    // -- per-param (amount, shape, oversample) -----------------------------------------

    "distort() per-param sets amount and shape" {
        val p = note("c").distort(0.5, "hard")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "hard"
        }
    }

    "distort() with amount only preserves backward compat" {
        val p = note("c").distort(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.7
        events[0].data.distortShape shouldBe null
    }

    "distort() per-param works as string extension" {
        val p = "c".distort(0.8, "fold")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.8
            distortShape shouldBe "fold"
        }
    }

    "distort() per-param works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort(0.5, "soft")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "soft"
        }
    }

    "distort() per-param works" {
        val p = note("c").distort(0.6, "diode")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.6
            distortShape shouldBe "diode"
        }
    }

    "distort() per-param mini-notation patterns" {
        val p = note("c3 e3").distort("<0.3 0.6>", "<soft hard>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.distort shouldBe 0.3
            cycle0[0].data.distortShape shouldBe "soft"

            cycle1.size shouldBe 2
            cycle1[0].data.distort shouldBe 0.6
            cycle1[0].data.distortShape shouldBe "hard"
        }
    }

    "distort() per-param works chained with other effects" {
        val p = note("c").apply(gain(0.8).distort(0.5, "fold"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }

    // -- distort(shape = ...) --------------------------------------------------------------------------------------

    "distort(shape = ...) dsl interface" {
        dslInterfaceTests(
            "pattern.distort(shape = shape)" to note("c").distort(shape = "fold"),
            "script pattern.distort(shape = shape)" to
                    SprudelPattern.compile("""note("c").distort(shape = "fold")"""),
            "string.distort(shape = shape)" to "c".distort(shape = "fold"),
            "script string.distort(shape = shape)" to
                    SprudelPattern.compile(""""c".distort(shape = "fold")"""),
            "distort(shape = shape)" to note("c").apply(distort(shape = "fold")),
            "script distort(shape = shape)" to
                    SprudelPattern.compile("""note("c").apply(distort(shape = "fold"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.distortShape shouldBe "fold"
        }
    }

    "distort(shape = ...) sets VoiceData.distortShape" {
        val p = note("c").distort(shape = "fold")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortShape shouldBe "fold"
    }

    "distort(shape = ...) works with control pattern" {
        val p = note("c3 e3").distort(shape = "<soft hard>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.distortShape shouldBe "soft"

            cycle1.size shouldBe 2
            cycle1[0].data.distortShape shouldBe "hard"
        }
    }

    "distort(shape = ...) converts to lowercase" {
        val p = note("c").distort(shape = "FOLD")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortShape shouldBe "fold"
    }

    "distort(shape = ...) chains with distort()" {
        val p = note("c").distort(0.5).distort(shape = "hard")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "hard"
        }
    }

    "distort(shape = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort(0.5).distort(shape = "fold")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }

    "PatternMapperFn.distort(shape = ...) chains correctly" {
        val p = note("c").apply(distort(0.5).distort(shape = "fold"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }

    "distort(tail-only) does not touch the head field" {
        // numeric receiver: without the tail-only guard the head apply would REINTERPRET
        // the values ("3"/"4") into the distort field
        val p = SprudelPattern.compile("""seq("3 4").distort(shape = "tube")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.distort shouldBe null
        events[0].data.distortShape shouldBe "tube"
    }
})
