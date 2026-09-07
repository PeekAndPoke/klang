/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangFmsustainSpec : StringSpec({

    "fmsustain dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.0 0.7"

        dslInterfaceTests(
            "pattern.fm(sustain = ctrl)" to s(pat).fm(sustain = ctrl),
            "script pattern.fm(sustain = ctrl)" to SprudelPattern.compile("""s("$pat").fm(sustain = "$ctrl")"""),
            "string.fm(sustain = ctrl)" to pat.fm(sustain = ctrl),
            "script string.fm(sustain = ctrl)" to SprudelPattern.compile(""""$pat".fm(sustain = "$ctrl")"""),
            "fm(sustain = ctrl)" to s(pat).apply(fm(sustain = ctrl)),
            "script fm(sustain = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(sustain = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "fmsus dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.0 0.7"

        dslInterfaceTests(
            "pattern.fm(sustain = ctrl)" to s(pat).fm(sustain = ctrl),
            "script pattern.fm(sustain = ctrl)" to SprudelPattern.compile("""s("$pat").fm(sustain = "$ctrl")"""),
            "string.fm(sustain = ctrl)" to pat.fm(sustain = ctrl),
            "script string.fm(sustain = ctrl)" to SprudelPattern.compile(""""$pat".fm(sustain = "$ctrl")"""),
            "fm(sustain = ctrl)" to s(pat).apply(fm(sustain = ctrl)),
            "script fm(sustain = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(sustain = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "top-level fm(sustain = ...) sets VoiceData.fmSustain correctly" {
        val p = s("hh hh").apply(fm(sustain = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fm(sustain = ...) sets VoiceData.fmSustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.fm(sustain = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmSustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fm(sustain = ...) works as string extension" {
        val p = "c3".fm(sustain = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmSustain shouldBe 0.5
    }

    "fm(sustain = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").fm(sustain = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

})
