/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * `penvCurves(attack, decay, release)`, the pitch envelope's curves door (phase 3 step 5b (c1), decision
 * D3): the `adsrCurves` rule on the `penv` stages. Door parity across the six forms, then the rule's
 * clauses one row each.
 */
class LangPenvCurvesSpec : StringSpec({

    "penvCurves dsl interface: sets the three stage curves, on both doors" {
        val pat = "0 1"

        dslInterfaceTests(
            "pattern.penvCurves(a, d, r)" to seq(pat).penvCurves("linear", "square", "cube"),
            "script pattern.penvCurves(a, d, r)" to SprudelPattern.compile("""seq("$pat").penvCurves("linear", "square", "cube")"""),
            "string.penvCurves(a, d, r)" to pat.penvCurves("linear", "square", "cube"),
            "script string.penvCurves(a, d, r)" to SprudelPattern.compile(""""$pat".penvCurves("linear", "square", "cube")"""),
            "penvCurves(a, d, r)" to seq(pat).apply(penvCurves("linear", "square", "cube")),
            "script penvCurves(a, d, r)" to SprudelPattern.compile("""seq("$pat").apply(penvCurves("linear", "square", "cube"))"""),
            "mapper.penvCurves(a, d, r)" to seq(pat).apply(penv(12).penvCurves("linear", "square", "cube")),
            "script mapper.penvCurves(a, d, r)" to
                SprudelPattern.compile("""seq("$pat").apply(penv(12).penvCurves("linear", "square", "cube"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.pAttackCurve shouldBe AdsrCurve.Linear
                events[0].data.pDecayCurve shouldBe AdsrCurve.Square
                events[0].data.pReleaseCurve shouldBe AdsrCurve.Cube
            }
        }
    }

    "an omitted stage keeps its current curve" {
        val events = note("c").penvCurves("linear", "linear", "linear").penvCurves(decay = "scurve").queryArc(0.0, 1.0)

        with(events[0].data) {
            pAttackCurve shouldBe AdsrCurve.Linear
            pDecayCurve shouldBe AdsrCurve.SCurve
            pReleaseCurve shouldBe AdsrCurve.Linear
        }
    }

    "an unknown name keeps the stage's current curve, it is not an error" {
        val events = note("c").penvCurves("cube", "cube", "cube").penvCurves("xyz", "square", "nope").queryArc(0.0, 1.0)

        with(events[0].data) {
            pAttackCurve shouldBe AdsrCurve.Cube
            pDecayCurve shouldBe AdsrCurve.Square
            pReleaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "names are case-insensitive and take the catalogue's aliases" {
        val events = note("c").penvCurves("LIN", " Exp ", "sigmoid").queryArc(0.0, 1.0)

        with(events[0].data) {
            pAttackCurve shouldBe AdsrCurve.Linear
            pDecayCurve shouldBe AdsrCurve.Exponential
            pReleaseCurve shouldBe AdsrCurve.SCurve
        }
    }

    "each stage is patternable" {
        val events = note("c e").penvCurves(decay = "<linear cube>").queryArc(1.0, 2.0)

        events.map { it.data.pDecayCurve } shouldBe listOf(AdsrCurve.Cube, AdsrCurve.Cube)
    }

    "a bare call changes nothing and reinterprets nothing" {
        val events = seq("5 7").penvCurves().queryArc(0.0, 1.0)

        events.map { it.data.pEnv } shouldBe listOf(null, null)
        events.map { it.data.pAttackCurve } shouldBe listOf(null, null)
    }

    "a curve alone switches no pitch envelope on: the wire carries no amount" {
        val vd = note("c").penvCurves("linear", "linear", "linear").queryArc(0.0, 1.0)[0].data.toVoiceData()

        vd.pEnv shouldBe null
        vd.pDecayCurve shouldBe AdsrCurve.Linear
    }
})
