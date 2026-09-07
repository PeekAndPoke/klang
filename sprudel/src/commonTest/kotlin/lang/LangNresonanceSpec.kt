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

class LangNresonanceSpec : StringSpec({

    // ---- nresonance ----

    "notch(q = ...) sets VoiceData.nresonance" {
        val p = note("a b").apply(notch(q = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(q = ...) sets VoiceData.nresonance on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(q = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nresonance } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(q = ...) works as string extension" {
        val p = "c3".notch(q = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nresonance shouldBe 0.5
    }

    "notch(q = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(q = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.5, 1.0)
    }

    "notch(q = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(q = ctrl)" to seq(pat).notch(q = ctrl),
            "script pattern.notch(q = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(q = "$ctrl")"""),
            "string.notch(q = ctrl)" to pat.notch(q = ctrl),
            "script string.notch(q = ctrl)" to SprudelPattern.compile(""""$pat".notch(q = "$ctrl")"""),
            "notch(q = ctrl)" to seq(pat).apply(notch(q = ctrl)),
            "script notch(q = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(q = "$ctrl"))"""),
            "chained notch(q = ctrl)" to seq(pat).apply(notch(q = ctrl).notch(q = ctrl)),
            "script chained notch(q = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(q = "$ctrl").notch(q = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nresonance shouldBe 0.5
            events[1].data.nresonance shouldBe 1.0
        }
    }

})
