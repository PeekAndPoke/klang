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

class LangNfreleaseSpec : StringSpec({

    // ---- nfrelease ----

    "notch(release = ...) sets VoiceData.nfrelease" {
        val p = note("a b").apply(notch(release = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    "control pattern notch(release = ...) sets VoiceData.nfrelease on existing pattern" {
        val base = note("c3 e3")
        val p = base.notch(release = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.nfrelease } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "notch(release = ...) works as string extension" {
        val p = "c3".notch(release = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.nfrelease shouldBe 0.5
    }

    "notch(release = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(release = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nfrelease } shouldBe listOf(0.5, 1.0)
    }

    "notch(release = ...) dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"

        dslInterfaceTests(
            "pattern.notch(release = ctrl)" to seq(pat).notch(release = ctrl),
            "script pattern.notch(release = ctrl)" to SprudelPattern.compile("""seq("$pat").notch(release = "$ctrl")"""),
            "string.notch(release = ctrl)" to pat.notch(release = ctrl),
            "script string.notch(release = ctrl)" to SprudelPattern.compile(""""$pat".notch(release = "$ctrl")"""),
            "notch(release = ctrl)" to seq(pat).apply(notch(release = ctrl)),
            "script notch(release = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(release = "$ctrl"))"""),
            "chained notch(release = ctrl)" to seq(pat).apply(notch(release = ctrl).notch(release = ctrl)),
            "script chained notch(release = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(notch(release = "$ctrl").notch(release = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.nfrelease shouldBe 0.5
            events[1].data.nfrelease shouldBe 1.0
        }
    }

})
