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

class LangNfattackSpec : StringSpec({

    // ---- nfattack ----

    "notch(attack = ...) sets VoiceData.nfattack" {
        val p = note("a b").apply(notch(attack = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(attack = ...) sets VoiceData.nfattack on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(attack = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfattack } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(attack = ...) works as string extension" {
        val p = "c3".notch(attack = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfattack shouldBe 0.5
    }

    "notch(attack = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(attack = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfattack } shouldBe listOf(0.5, 1.0)
    }

    "notch(attack = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(attack = ctrl)" to seq(pat).notch(attack = ctrl),
            "script pattern.notch(attack = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(attack = "$ctrl")"""),
            "string.notch(attack = ctrl)" to pat.notch(attack = ctrl),
            "script string.notch(attack = ctrl)" to SprudelPattern.compile(""""$pat".notch(attack = "$ctrl")"""),
            "notch(attack = ctrl)" to seq(pat).apply(notch(attack = ctrl)),
            "script notch(attack = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(attack = "$ctrl"))"""),
            "chained notch(attack = ctrl)" to seq(pat).apply(notch(attack = ctrl).notch(attack = ctrl)),
            "script chained notch(attack = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(attack = "$ctrl").notch(attack = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfattack shouldBe 0.5
            events[1].data.nfattack shouldBe 1.0
        }
    }

})
