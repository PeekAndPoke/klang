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
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/** The first [IgnitorDsl.PitchEnvelope] on this tree's spine (each node's first child). */
private fun IgnitorDsl.findPitchEnvelope(): IgnitorDsl.PitchEnvelope {
    var node: IgnitorDsl = this

    while (node !is IgnitorDsl.PitchEnvelope) {
        node = node.childNodes().first()
    }

    return node
}

class LangPitchEnvelopeSpec : StringSpec({

    // ---- sustain (replaced the anchor, phase 3 step 5b (c1)) ----

    "every penv form forwards sustain and release to their own slots, by name and positionally" {
        // Sustain and release are the two slots a forwarding call can swap unseen: both are numbers with
        // neighbouring positions. Distinct values in each, on all forms of the door, mapper and chained
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

        val named = "semitones = 12, attack = 0.01, decay = 0.2, sustain = 0.5, release = 0.3"
        val positional = "12, 0.01, 0.2, 0.5, 0.3"

        for (args in listOf(named, positional)) {
            withClue("penv($args)") {
                check(
                    listOf(
                        "script pattern" to SprudelPattern.compile("""note("c").penv($args)"""),
                        "script string" to SprudelPattern.compile(""""c".penv($args)"""),
                        "script mapper" to SprudelPattern.compile("""note("c").apply(penv($args))"""),
                        "script chained mapper" to SprudelPattern.compile("""note("c").apply(gain(1).penv($args))"""),
                    ),
                )
            }
        }

        check(
            listOf(
                "penv pattern" to note("c").penv(semitones = 12, attack = 0.01, decay = 0.2, sustain = 0.5, release = 0.3),
                "penv string" to "c".penv(12, 0.01, 0.2, 0.5, 0.3),
                "penv mapper" to note("c").apply(penv(12, 0.01, 0.2, 0.5, 0.3)),
                "penv chained mapper" to note("c").apply(gain(1).penv(12, 0.01, 0.2, 0.5, 0.3)),
            ),
        )
    }

    // ---- The tail-only guard: one row per term (the 2026-09-24 ledger rule) ----
    //
    // Each tail-only row runs on a NUMERIC receiver, `seq("5 7")`, where a wrongly fired bare
    // reinterpret would write 5 and 7 into the semitones; on a `note(...)` receiver that misfire writes
    // nothing and a deleted term would stay green.

    "tail-only: penv(attack = ...) leaves the semitones untouched on a numeric receiver" {
        seq("5 7").penv(attack = 0.01).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(decay = ...) leaves the semitones untouched on a numeric receiver" {
        seq("5 7").penv(decay = 0.1).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(sustain = ...) leaves the semitones untouched on a numeric receiver" {
        seq("5 7").penv(sustain = 0.3).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "tail-only: penv(release = ...) leaves the semitones untouched on a numeric receiver" {
        seq("5 7").penv(release = 0.2).queryArc(0.0, 1.0).map { it.data.pEnv } shouldBe listOf(null, null)
    }

    "semitones WITH a stage is written: the semitones term of the guard" {
        val events = seq("5 7").penv(semitones = 24, attack = 0.01).queryArc(0.0, 1.0)

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

    "toVoiceData carries every pitch envelope knob and curve to the wire as classic()'s penv slots" {
        // Pitch pipeline step 1: the eight typed wire fields are gone, the door fills `classic()`'s pitch envelope
        // stage through its slots (curves as their `AdsrCurves` index).
        val bag = note("c").penv(12, 0.01, 0.2, 0.5, 0.3).penvCurves("linear", "scurve", "cube")
            .queryArc(0.0, 1.0)[0].data.toVoiceData().ignitorParams!!

        bag["penv.semitones"] shouldBe 12.0
        bag["penv.attack"] shouldBe 0.01
        bag["penv.decay"] shouldBe 0.2
        bag["penv.sustain"] shouldBe 0.5
        bag["penv.release"] shouldBe 0.3
        bag["penvCurves.attack"] shouldBe AdsrCurves.indexOf(AdsrCurve.Linear)
        bag["penvCurves.decay"] shouldBe AdsrCurves.indexOf(AdsrCurve.SCurve)
        bag["penvCurves.release"] shouldBe AdsrCurves.indexOf(AdsrCurve.Cube)
    }

    "door parity: penv's semitones is one word on the sprudel door, the slot and the Ignitor node" {
        // The sprudel door's parameter, its reader and the slot `classic()` hands the node's `semitones` knob carry
        // the same word (decision D4); `amount` is retired (`docs/retired-names.md`).
        val slots = IgnitorDsl.Slots.penv
        val stage = IgnitorDsl.Sine().classic().findPitchEnvelope()

        stage.semitones shouldBe slots.semitones
        stage.attack shouldBe slots.attack
        stage.decay shouldBe slots.decay
        stage.sustain shouldBe slots.sustain
        stage.release shouldBe slots.release
        stage.attackCurve shouldBe IgnitorDsl.Slots.penvCurves.attack
        stage.decayCurve shouldBe IgnitorDsl.Slots.penvCurves.decay
        stage.releaseCurve shouldBe IgnitorDsl.Slots.penvCurves.release

        val bag = SprudelPattern.compile("""note("c").penv(semitones = 7)""")!!.queryArc(0.0, 1.0)[0].data.toVoiceData().ignitorParams!!

        bag[(slots.semitones as IgnitorDsl.Param).name] shouldBe 7.0
        SprudelPattern.compile("""note("c").penv(7).pan(penv.semitones)""")!!.queryArc(0.0, 1.0)[0].data.pan shouldBe 7.0
    }
})
