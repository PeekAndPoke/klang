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

class LangNotchfSpec : StringSpec({

    // ---- notchf ----

    "notchf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"

        dslInterfaceTests(
            "pattern.notch(ctrl)" to seq(pat).notch(ctrl),
            "script pattern.notch(ctrl)" to SprudelPattern.compile("""seq("$pat").notch("$ctrl")"""),
            "string.notch(ctrl)" to pat.notch(ctrl),
            "script string.notch(ctrl)" to SprudelPattern.compile(""""$pat".notch("$ctrl")"""),
            "notch(ctrl)" to seq(pat).apply(notch(ctrl)),
            "script notch(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | seq(\"1000 500\").notch()" {
        val p = seq("1000 500").notch()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | \"1000 500\".notch()" {
        val p = "1000 500".notch()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "reinterpret voice data as notchf | seq(\"1000 500\").apply(notch())" {
        val p = seq("1000 500").apply(notch())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.notchf shouldBe 1000.0
            events[1].data.notchf shouldBe 500.0
        }
    }

    "notch() sets VoiceData.notchf" {
        val p = note("a b").apply(notch("1000 500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.notchf shouldBe 1000.0
        events[1].data.notchf shouldBe 500.0
    }

    "notch() works as pattern extension" {
        val p = note("c").notch("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notch() works as string extension" {
        val p = "c".notch("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notch() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").notch("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.notchf shouldBe 1000.0
    }

    "notch() with continuous pattern sets notchf correctly" {
        val p = note("a b c d").notch(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.notchf shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.notchf shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.notchf shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.notchf shouldBe (0.0 plusOrMinus EPSILON)
    }
})
