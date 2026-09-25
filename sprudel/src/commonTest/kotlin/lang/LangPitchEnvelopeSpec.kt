/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

    // ---- sustain (replaced the anchor, phase 3 step 5b (c1)) ----

    "psustain dsl interface" {
        val pat = "c4 e4"
        val amount = 0.4

        dslInterfaceTests(
            "pattern.penv(sustain = v)" to note(pat).penv(sustain = amount),
            "script pattern.penv(sustain = v)" to SprudelPattern.compile("""note("$pat").penv(sustain = $amount)"""),
            "string.penv(sustain = v)" to pat.penv(sustain = amount),
            "script string.penv(sustain = v)" to SprudelPattern.compile(""""$pat".penv(sustain = $amount)"""),
            "penv(sustain = v)" to note(pat).apply(penv(sustain = amount)),
            "script penv(sustain = v)" to SprudelPattern.compile("""note("$pat").apply(penv(sustain = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pSustain shouldBe amount
        }
    }

    "penv(sustain = ...) sets SprudelVoiceData.pSustain" {
        val p = note("a b").penv(sustain = "0.0 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pSustain } shouldBe listOf(0.0, 1.0)
    }

    "the five positional arguments are amount, attack, decay, SUSTAIN, release, on both doors" {
        dslInterfaceTests(
            "pattern.penv(a, b, c, d, e)" to note("c").penv(12, 0.01, 0.2, 0.5, 0.3),
            "script pattern.penv(a, b, c, d, e)" to SprudelPattern.compile("""note("c").penv(12, 0.01, 0.2, 0.5, 0.3)"""),
            "pattern.pamt(a, b, c, d, e)" to note("c").pamt(12, 0.01, 0.2, 0.5, 0.3),
            "script pattern.pamt(a, b, c, d, e)" to SprudelPattern.compile("""note("c").pamt(12, 0.01, 0.2, 0.5, 0.3)"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            with(events[0].data) {
                pEnv shouldBe 12.0
                pAttack shouldBe 0.01
                pDecay shouldBe 0.2
                pSustain shouldBe 0.5
                pRelease shouldBe 0.3
            }
        }
    }

    "every penv and pamt form forwards sustain and release to their own slots, by name and positionally" {
        // Sustain and release are the two slots a forwarding call can swap unseen: both are numbers with
        // neighbouring positions. Distinct values in each, on all forms of both names, mapper and chained
        // mapper included (the chain forwards its own argument list).
        fun check(forms: List<Pair<String, SprudelPattern?>>) = dslInterfaceTests(*forms.toTypedArray()) { _, events ->
            events.shouldNotBeEmpty()
            with(events[0].data) {
                pEnv shouldBe 12.0
                pAttack shouldBe 0.01
                pDecay shouldBe 0.2
                pSustain shouldBe 0.5
                pRelease shouldBe 0.3
            }
        }

        for (name in listOf("penv", "pamt")) {
            val named = "amount = 12, attack = 0.01, decay = 0.2, sustain = 0.5, release = 0.3"
            val positional = "12, 0.01, 0.2, 0.5, 0.3"

            for (args in listOf(named, positional)) {
                withClue("$name($args)") {
                    check(
                        listOf(
                            "script pattern" to SprudelPattern.compile("""note("c").$name($args)"""),
                            "script string" to SprudelPattern.compile(""""c".$name($args)"""),
                            "script mapper" to SprudelPattern.compile("""note("c").apply($name($args))"""),
                            "script chained mapper" to SprudelPattern.compile("""note("c").apply(gain(1).$name($args))"""),
                        ),
                    )
                }
            }
        }

        check(
            listOf(
                "penv pattern" to note("c").penv(amount = 12, attack = 0.01, decay = 0.2, sustain = 0.5, release = 0.3),
                "penv string" to "c".penv(12, 0.01, 0.2, 0.5, 0.3),
                "penv mapper" to note("c").apply(penv(12, 0.01, 0.2, 0.5, 0.3)),
                "penv chained mapper" to note("c").apply(gain(1).penv(12, 0.01, 0.2, 0.5, 0.3)),
                "pamt pattern" to note("c").pamt(12, 0.01, 0.2, 0.5, 0.3),
                "pamt string" to "c".pamt(12, 0.01, 0.2, 0.5, 0.3),
                "pamt mapper" to note("c").apply(pamt(12, 0.01, 0.2, 0.5, 0.3)),
                "pamt chained mapper" to note("c").apply(gain(1).pamt(12, 0.01, 0.2, 0.5, 0.3)),
            ),
        )
    }

    // ---- The tail-only guard: one row per term (the 2026-09-24 ledger rule) ----
    //
    // Each tail-only row runs on a NUMERIC receiver, `seq("5 7")`, where a wrongly fired bare
    // reinterpret would write 5 and 7 into the amount; on a `note(...)` receiver that misfire writes
    // nothing and a deleted term would stay green.

    "tail-only: penv(attack = ...) leaves the amount untouched on a numeric receiver" {
        seq("5 7").penv(attack = 0.01).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(decay = ...) leaves the amount untouched on a numeric receiver" {
        seq("5 7").penv(decay = 0.1).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(sustain = ...) leaves the amount untouched on a numeric receiver" {
        seq("5 7").penv(sustain = 0.3).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(release = ...) leaves the amount untouched on a numeric receiver" {
        seq("5 7").penv(release = 0.2).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "an amount WITH a stage is written: the amount term of the guard" {
        val events = seq("5 7").penv(amount = 24, attack = 0.01).queryArc(0.0, 1.0)

        events.map { it.data.pEnv } shouldBe listOf(24.0, 24.0)
        events.map { it.data.pAttack } shouldBe listOf(0.01, 0.01)
    }

    // ---- The retired surface fails loudly ----

    "the retired curve and anchor slots are gone on the script door, loudly" {
        // Each error names the retired word, so it fails for the right reason and not a typo elsewhere.
        for ((code, word) in listOf(
            """note("c").penv(curve = 1)""" to "curve",
            """note("c").penv(anchor = 0.5)""" to "anchor",
            """note("c").penv(12).pan(penv.curve)""" to "curve",
            """note("c").penv(12).pan(penv.anchor)""" to "anchor",
        )) {
            withClue(code) {
                shouldThrowAny { SprudelPattern.compile(code)!!.queryArc(0.0, 1.0) }.message shouldContain word
            }
        }
    }

    // ---- Combined test ----

    "pitch envelope functions work together" {
        val p = note("c").penv(attack = "0.1", decay = "0.3", sustain = "0.2", release = "0.5", amount = "12")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pAttack shouldBe 0.1
        events[0].data.pDecay shouldBe 0.3
        events[0].data.pSustain shouldBe 0.2
        events[0].data.pRelease shouldBe 0.5
        events[0].data.pEnv shouldBe 12.0
    }
})
