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

class LangFmattackSpec : StringSpec({

    "fmattack dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fm(attack = ctrl)" to s(pat).fm(attack = ctrl),
            "script pattern.fm(attack = ctrl)" to SprudelPattern.compile("""s("$pat").fm(attack = "$ctrl")"""),
            "string.fm(attack = ctrl)" to pat.fm(attack = ctrl),
            "script string.fm(attack = ctrl)" to SprudelPattern.compile(""""$pat".fm(attack = "$ctrl")"""),
            "fm(attack = ctrl)" to s(pat).apply(fm(attack = ctrl)),
            "script fm(attack = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(attack = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "fmatt dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fm(attack = ctrl)" to s(pat).fm(attack = ctrl),
            "script pattern.fm(attack = ctrl)" to SprudelPattern.compile("""s("$pat").fm(attack = "$ctrl")"""),
            "string.fm(attack = ctrl)" to pat.fm(attack = ctrl),
            "script string.fm(attack = ctrl)" to SprudelPattern.compile(""""$pat".fm(attack = "$ctrl")"""),
            "fm(attack = ctrl)" to s(pat).apply(fm(attack = ctrl)),
            "script fm(attack = ctrl)" to SprudelPattern.compile("""s("$pat").apply(fm(attack = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmAttack shouldBe 0.1
            events[1].data.fmAttack shouldBe 0.5
        }
    }

    "top-level fm(attack = ...) sets VoiceData.fmAttack correctly" {
        val p = s("hh hh").apply(fm(attack = "0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fm(attack = ...) sets VoiceData.fmAttack on existing pattern" {
        val base = note("c3 e3")
        val p = base.fm(attack = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmAttack } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fm(attack = ...) works as string extension" {
        val p = "c3".fm(attack = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmAttack shouldBe 0.5
    }

    "fm(attack = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").fm(attack = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmAttack } shouldBe listOf(0.5, 1.0)
    }

})
