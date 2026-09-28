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
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangPitchEnvelopeSpec : StringSpec({

    // ---- sustain (replaced the anchor, phase 3 step 5b (c1)) ----

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

    // ---- The wire ----

    "toVoiceData carries every pitch envelope slot and curve to the wire" {
        val vd = note("c").penv(12, 0.01, 0.2, 0.5, 0.3).penvCurves("linear", "scurve", "cube")
            .queryArc(0.0, 1.0)[0].data.toVoiceData()

        vd.pEnv shouldBe 12.0
        vd.pAttack shouldBe 0.01
        vd.pDecay shouldBe 0.2
        vd.pSustain shouldBe 0.5
        vd.pRelease shouldBe 0.3
        vd.pAttackCurve shouldBe AdsrCurve.Linear
        vd.pDecayCurve shouldBe AdsrCurve.SCurve
        vd.pReleaseCurve shouldBe AdsrCurve.Cube
    }
})
