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

class LangFmhSpec : StringSpec({

    "fmh dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.fm(h = ctrl)" to s(pat).fm(h = ctrl),
            "script pattern.fm(h = ctrl)" to SprudelPattern.compile("""s("$pat").fm(h = "$ctrl")"""),
            "string.fm(h = ctrl)" to pat.fm(h = ctrl),
            "script string.fm(h = ctrl)" to SprudelPattern.compile(""""$pat".fm(h = "$ctrl")"""),
            "fm(h = ctrl)" to s(pat).apply(fm(h = ctrl)),
            "script fm(h = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(h = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmh shouldBe 0.5
            events[1].data.fmh shouldBe 1.0
        }
    }

    "top-level fm(h = ...) sets VoiceData.fmh correctly" {
        val p = s("hh hh").apply(fm(h = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fm(h = ...) sets VoiceData.fmh on existing pattern" {
        val base = note("c3 e3")
        val p = base.fm(h = "0.5 1.0")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0, 0.5, 1.0)
    }

    "fm(h = ...) works as string extension" {
        val p = "c3".fm(h = "2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmh shouldBe 2.0
    }

    "fm(h = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").fm(h = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmh } shouldBe listOf(0.5, 1.0)
    }
})
