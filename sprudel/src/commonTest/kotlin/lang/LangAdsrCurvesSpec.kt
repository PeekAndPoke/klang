/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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

class LangAdsrCurvesSpec : StringSpec({

    "adsrCurves dsl interface — sets per-stage curves" {
        val pat = "0 1"

        dslInterfaceTests(
            "pattern.adsrCurves(a, d, r)" to
                    seq(pat).adsrCurves("linear", "square", "cube"),
            "script pattern.adsrCurves(a, d, r)" to
                    SprudelPattern.compile("""seq("$pat").adsrCurves("linear", "square", "cube")"""),
            "string.adsrCurves(a, d, r)" to
                    pat.adsrCurves("linear", "square", "cube"),
            "script string.adsrCurves(a, d, r)" to
                    SprudelPattern.compile(""""$pat".adsrCurves("linear", "square", "cube")"""),
            "adsrCurves(a, d, r)" to
                    seq(pat).apply(adsrCurves("linear", "square", "cube")),
            "script adsrCurves(a, d, r)" to
                    SprudelPattern.compile("""seq("$pat").apply(adsrCurves("linear", "square", "cube"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.attackCurve shouldBe AdsrCurve.Linear
                events[0].data.decayCurve shouldBe AdsrCurve.Square
                events[0].data.releaseCurve shouldBe AdsrCurve.Cube
            }
        }
    }

    "adsrCurves() partial input — leaves missing stages untouched" {
        val p = "0 1".apply(adsrCurves(decay = "square", release = "cube"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        with(events[0].data) {
            attackCurve shouldBe null    // empty first part — left null
            decayCurve shouldBe AdsrCurve.Square
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurves() accepts case-insensitive names" {
        val p = "0".apply(adsrCurves("LINEAR", "Square", "CUBE"))
        val events = p.queryArc(0.0, 1.0)
        with(events[0].data) {
            attackCurve shouldBe AdsrCurve.Linear
            decayCurve shouldBe AdsrCurve.Square
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurves() invalid name leaves stage untouched" {
        val p = "0".apply(adsrCurves("xyz", "square", "cube"))
        val events = p.queryArc(0.0, 1.0)
        with(events[0].data) {
            attackCurve shouldBe null
            decayCurve shouldBe AdsrCurve.Square
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurve() applies same curve to all three stages" {
        val p = "0".apply(adsrCurve("cube"))
        val events = p.queryArc(0.0, 1.0)
        with(events[0].data) {
            attackCurve shouldBe AdsrCurve.Cube
            decayCurve shouldBe AdsrCurve.Cube
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurve('linear') restores plastic feel on all stages" {
        val p = "0".apply(adsrCurve("linear"))
        val events = p.queryArc(0.0, 1.0)
        with(events[0].data) {
            attackCurve shouldBe AdsrCurve.Linear
            decayCurve shouldBe AdsrCurve.Linear
            releaseCurve shouldBe AdsrCurve.Linear
        }
    }
})
