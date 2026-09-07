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

class LangNfenvSpec : StringSpec({

    // ---- nfenv ----

    "notch(env = ...) sets VoiceData.nfenv" {
        val p = note("a b").apply(notch(env = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(env = ...) sets VoiceData.nfenv on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(env = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfenv } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(env = ...) works as string extension" {
        val p = "c3".notch(env = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfenv shouldBe 0.5
    }

    "notch(env = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(env = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfenv } shouldBe listOf(0.5, 1.0)
    }

    "notch(env = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(env = ctrl)" to seq(pat).notch(env = ctrl),
            "script pattern.notch(env = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(env = "$ctrl")"""),
            "string.notch(env = ctrl)" to pat.notch(env = ctrl),
            "script string.notch(env = ctrl)" to SprudelPattern.compile(""""$pat".notch(env = "$ctrl")"""),
            "notch(env = ctrl)" to seq(pat).apply(notch(env = ctrl)),
            "script notch(env = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(env = "$ctrl"))"""),
            "chained notch(env = ctrl)" to seq(pat).apply(notch(env = ctrl).notch(env = ctrl)),
            "script chained notch(env = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(env = "$ctrl").notch(env = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfenv shouldBe 0.5
            events[1].data.nfenv shouldBe 1.0
        }
    }

})
