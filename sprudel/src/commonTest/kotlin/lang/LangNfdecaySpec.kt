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

class LangNfdecaySpec : StringSpec({

    // ---- nfdecay ----

    "notch(decay = ...) sets VoiceData.nfdecay" {
        val p = note("a b").apply(notch(decay = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(decay = ...) sets VoiceData.nfdecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(decay = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfdecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(decay = ...) works as string extension" {
        val p = "c3".notch(decay = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfdecay shouldBe 0.5
    }

    "notch(decay = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(decay = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfdecay } shouldBe listOf(0.5, 1.0)
    }

    "notch(decay = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(decay = ctrl)" to seq(pat).notch(decay = ctrl),
            "script pattern.notch(decay = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(decay = "$ctrl")"""),
            "string.notch(decay = ctrl)" to pat.notch(decay = ctrl),
            "script string.notch(decay = ctrl)" to SprudelPattern.compile(""""$pat".notch(decay = "$ctrl")"""),
            "notch(decay = ctrl)" to seq(pat).apply(notch(decay = ctrl)),
            "script notch(decay = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(decay = "$ctrl"))"""),
            "chained notch(decay = ctrl)" to seq(pat).apply(notch(decay = ctrl).notch(decay = ctrl)),
            "script chained notch(decay = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(decay = "$ctrl").notch(decay = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfdecay shouldBe 0.5
            events[1].data.nfdecay shouldBe 1.0
        }
    }

})
