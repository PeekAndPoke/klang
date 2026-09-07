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

class LangFmdecaySpec : StringSpec({

    "fmdecay dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fm(decay = ctrl)" to s(pat).fm(decay = ctrl),
            "script pattern.fm(decay = ctrl)" to SprudelPattern.compile("""s("$pat").fm(decay = "$ctrl")"""),
            "string.fm(decay = ctrl)" to pat.fm(decay = ctrl),
            "script string.fm(decay = ctrl)" to SprudelPattern.compile(""""$pat".fm(decay = "$ctrl")"""),
            "fm(decay = ctrl)" to s(pat).apply(fm(decay = ctrl)),
            "script fm(decay = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(decay = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "fmdec dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fm(decay = ctrl)" to s(pat).fm(decay = ctrl),
            "script pattern.fm(decay = ctrl)" to SprudelPattern.compile("""s("$pat").fm(decay = "$ctrl")"""),
            "string.fm(decay = ctrl)" to pat.fm(decay = ctrl),
            "script string.fm(decay = ctrl)" to SprudelPattern.compile(""""$pat".fm(decay = "$ctrl")"""),
            "fm(decay = ctrl)" to s(pat).apply(fm(decay = ctrl)),
            "script fm(decay = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(decay = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "top-level fm(decay = ...) sets VoiceData.fmDecay correctly" {
        val p = s("hh hh").apply(fm(decay = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fm(decay = ...) sets VoiceData.fmDecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.fm(decay = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmDecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fm(decay = ...) works as string extension" {
        val p = "c3".fm(decay = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmDecay shouldBe 0.5
    }

    "fm(decay = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").fm(decay = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

})
