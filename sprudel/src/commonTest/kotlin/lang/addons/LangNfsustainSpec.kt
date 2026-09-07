/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seq

class LangNfsustainSpec : StringSpec({

    // ---- nfsustain ----

    "notch(sustain = ...) sets VoiceData.nfsustain" {
        val p = note("a b").apply(notch(sustain = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(sustain = ...) sets VoiceData.nfsustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(sustain = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfsustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(sustain = ...) works as string extension" {
        val p = "c3".notch(sustain = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfsustain shouldBe 0.5
    }

    "notch(sustain = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(sustain = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfsustain } shouldBe listOf(0.5, 1.0)
    }

    "notch(sustain = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(sustain = ctrl)" to seq(pat).notch(sustain = ctrl),
            "script pattern.notch(sustain = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(sustain = "$ctrl")"""),
            "string.notch(sustain = ctrl)" to pat.notch(sustain = ctrl),
            "script string.notch(sustain = ctrl)" to SprudelPattern.compile(""""$pat".notch(sustain = "$ctrl")"""),
            "notch(sustain = ctrl)" to seq(pat).apply(notch(sustain = ctrl)),
            "script notch(sustain = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(sustain = "$ctrl"))"""),
            "chained notch(sustain = ctrl)" to seq(pat).apply(notch(sustain = ctrl).notch(sustain = ctrl)),
            "script chained notch(sustain = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(sustain = "$ctrl").notch(sustain = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfsustain shouldBe 0.5
            events[1].data.nfsustain shouldBe 1.0
        }
    }

})
