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

class LangPitchEnvelopeSpec : StringSpec({

    // ---- pattack / patt ----

    "pattack dsl interface" {
        val pat = "c4 e4"
        val amount = 0.1

        dslInterfaceTests(
            "pattern.penv(attack = v)" to note(pat).penv(attack = amount),
            "script pattern.penv(attack = v)" to SprudelPattern.compile("""note("$pat").penv(attack = $amount)"""),
            "string.penv(attack = v)" to pat.penv(attack = amount),
            "script string.penv(attack = v)" to SprudelPattern.compile(""""$pat".penv(attack = $amount)"""),
            "penv(attack = v)" to note(pat).apply(penv(attack = amount)),
            "script penv(attack = v)" to SprudelPattern.compile("""note("$pat").apply(penv(attack = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pAttack shouldBe amount
        }
    }

    "penv(attack = ...) sets SprudelVoiceData.pAttack" {
        val p = note("a b").penv(attack = "0.1 0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAttack } shouldBe listOf(0.1, 0.2)
    }

    "penv(attack = ...) works as pattern extension" {
        val p = note("c").penv(attack = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "penv(attack = ...) works as string extension" {
        val p = "c".penv(attack = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    "penv(attack = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").penv(attack = "0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
    }

    // ---- pdecay / pdec ----

    "pdecay dsl interface" {
        val pat = "c4 e4"
        val amount = 0.3

        dslInterfaceTests(
            "pattern.penv(decay = v)" to note(pat).penv(decay = amount),
            "script pattern.penv(decay = v)" to SprudelPattern.compile("""note("$pat").penv(decay = $amount)"""),
            "string.penv(decay = v)" to pat.penv(decay = amount),
            "script string.penv(decay = v)" to SprudelPattern.compile(""""$pat".penv(decay = $amount)"""),
            "penv(decay = v)" to note(pat).apply(penv(decay = amount)),
            "script penv(decay = v)" to SprudelPattern.compile("""note("$pat").apply(penv(decay = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pDecay shouldBe amount
        }
    }

    "penv(decay = ...) sets SprudelVoiceData.pDecay" {
        val p = note("a b").penv(decay = "0.3 0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pDecay } shouldBe listOf(0.3, 0.4)
    }

    "penv(decay = ...) works as pattern extension" {
        val p = note("c").penv(decay = "0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pDecay shouldBe 0.3
    }

    // ---- prelease / prel ----

    "prelease dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.penv(release = v)" to note(pat).penv(release = amount),
            "script pattern.penv(release = v)" to SprudelPattern.compile("""note("$pat").penv(release = $amount)"""),
            "string.penv(release = v)" to pat.penv(release = amount),
            "script string.penv(release = v)" to SprudelPattern.compile(""""$pat".penv(release = $amount)"""),
            "penv(release = v)" to note(pat).apply(penv(release = amount)),
            "script penv(release = v)" to SprudelPattern.compile("""note("$pat").apply(penv(release = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pRelease shouldBe amount
        }
    }

    "penv(release = ...) sets SprudelVoiceData.pRelease" {
        val p = note("a b").penv(release = "0.5 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pRelease } shouldBe listOf(0.5, 0.6)
    }

    "penv(release = ...) works as pattern extension" {
        val p = note("c").penv(release = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pRelease shouldBe 0.5
    }

    // ---- penv / pamt ----

    "penv dsl interface" {
        val pat = "c4 e4"
        val amount = 12.0

        dslInterfaceTests(
            "pattern.penv(v)" to note(pat).penv(amount),
            "script pattern.penv(v)" to SprudelPattern.compile("""note("$pat").penv($amount)"""),
            "string.penv(v)" to pat.penv(amount),
            "script string.penv(v)" to SprudelPattern.compile(""""$pat".penv($amount)"""),
            "penv(v)" to note(pat).apply(penv(amount)),
            "script penv(v)" to SprudelPattern.compile("""note("$pat").apply(penv($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pEnv shouldBe amount
        }
    }

    "reinterpret voice data as pEnv | seq(\"12 24\").penv()" {
        val p = seq("12 24").penv()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(12.0, 24.0)
    }

    "penv() sets SprudelVoiceData.pEnv" {
        val p = note("a b").penv("12 24")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pEnv } shouldBe listOf(12.0, 24.0)
    }

    "penv() works as pattern extension" {
        val p = note("c").penv("12")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pEnv shouldBe 12.0
    }

    "pamt() is an alias for penv()" {
        val p = note("c").pamt("12")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.pEnv shouldBe 12.0
    }

    // ---- pcurve / pcrv ----

    "pcurve dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.penv(curve = v)" to note(pat).penv(curve = amount),
            "script pattern.penv(curve = v)" to SprudelPattern.compile("""note("$pat").penv(curve = $amount)"""),
            "string.penv(curve = v)" to pat.penv(curve = amount),
            "script string.penv(curve = v)" to SprudelPattern.compile(""""$pat".penv(curve = $amount)"""),
            "penv(curve = v)" to note(pat).apply(penv(curve = amount)),
            "script penv(curve = v)" to SprudelPattern.compile("""note("$pat").apply(penv(curve = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pCurve shouldBe amount
        }
    }

    "penv(curve = ...) sets SprudelVoiceData.pCurve" {
        val p = note("a b").penv(curve = "0.5 1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pCurve } shouldBe listOf(0.5, 1.5)
    }

    "penv(curve = ...) works as pattern extension" {
        val p = note("c").penv(curve = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pCurve shouldBe 0.5
    }

    // ---- panchor / panc ----

    "panchor dsl interface" {
        val pat = "c4 e4"
        val amount = 0.0

        dslInterfaceTests(
            "pattern.penv(anchor = v)" to note(pat).penv(anchor = amount),
            "script pattern.penv(anchor = v)" to SprudelPattern.compile("""note("$pat").penv(anchor = $amount)"""),
            "string.penv(anchor = v)" to pat.penv(anchor = amount),
            "script string.penv(anchor = v)" to SprudelPattern.compile(""""$pat".penv(anchor = $amount)"""),
            "penv(anchor = v)" to note(pat).apply(penv(anchor = amount)),
            "script penv(anchor = v)" to SprudelPattern.compile("""note("$pat").apply(penv(anchor = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pAnchor shouldBe amount
        }
    }

    "penv(anchor = ...) sets SprudelVoiceData.pAnchor" {
        val p = note("a b").penv(anchor = "0.0 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.0, 1.0)
    }

    "penv(anchor = ...) works as pattern extension" {
        val p = note("c").penv(anchor = "0.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAnchor shouldBe 0.0
    }

    // ---- Combined test ----

    "pitch envelope functions work together" {
        val p = note("c").penv(attack = "0.1", decay = "0.3", release = "0.5", amount = "12")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
        events[0].data.pDecay shouldBe 0.3
        events[0].data.pRelease shouldBe 0.5
        events[0].data.pEnv shouldBe 12.0
    }
})
