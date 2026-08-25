/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * ADSR curve default pin (maintainer decision, 2026-08-24): NOTHING SET means
 * [AdsrCurve.Default] (= Exponential) on every stage and every door. On the IGNITOR door,
 * bare `adsrCurve()`/`adsrCurves()` also mean exp and an unrecognized name coerces to exp.
 * (The SPRUDEL door deliberately differs on those two: a bare control call is a no-op and
 * a bad name keeps the prior curve — per-event control-pattern semantics, pinned by
 * `LangAdsrCurvesSpec`; only the UNSET default is shared across doors.) The ignitor door
 * used to default attack/release to Square while the strip wire resolved Exponential —
 * one knob, two sounds. This spec is the tripwire.
 */
class LangAdsrCurveDefaultSpec : StringSpec({

    "sprudel wire: unset curves resolve to Exponential on every stage" {
        val resolved = AdsrDef.Std().resolve()
        resolved.attackCurve shouldBe AdsrCurve.Exponential
        resolved.decayCurve shouldBe AdsrCurve.Exponential
        resolved.releaseCurve shouldBe AdsrCurve.Exponential

        // and with EMPTY defaults too — this reaches the literal `?: AdsrCurve.Default`
        // fallbacks (defaultSynth carries its own curves, shadowing them otherwise)
        val bare = AdsrDef.Std().resolve(defaults = AdsrDef.Std())
        bare.attackCurve shouldBe AdsrCurve.Exponential
        bare.decayCurve shouldBe AdsrCurve.Exponential
        bare.releaseCurve shouldBe AdsrCurve.Exponential
    }

    "sprudel door: a pattern without curve calls carries NO curve on the wire (engine default rules)" {
        val data = SprudelPattern.compile("""note("c").adsr(0.01, 0.1, 0.7, 0.3)""")!!
            .queryArc(0.0, 1.0).first().data
        data.attackCurve shouldBe null
        data.decayCurve shouldBe null
        data.releaseCurve shouldBe null
    }

    "ignitor node: unset curves are null — and the script door's bare adsrCurve() means exp" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        // no call at all -> null on the node (the runtime resolves null to Exponential)
        val plain = eval("""Osc.saw().adsr(0.01, 0.1, 0.7, 0.3)""") as IgnitorDsl.Adsr
        plain.attackCurve shouldBe null
        plain.decayCurve shouldBe null
        plain.releaseCurve shouldBe null

        // bare adsrCurve() -> Exponential on every stage
        val bare = eval("""Osc.saw().adsr(0.01, 0.1, 0.7, 0.3).adsrCurve()""") as IgnitorDsl.Adsr
        bare.attackCurve shouldBe AdsrCurve.Exponential
        bare.decayCurve shouldBe AdsrCurve.Exponential
        bare.releaseCurve shouldBe AdsrCurve.Exponential

        // bare adsrCurves() -> same
        val bares = eval("""Osc.saw().adsr(0.01, 0.1, 0.7, 0.3).adsrCurves()""") as IgnitorDsl.Adsr
        bares.attackCurve shouldBe AdsrCurve.Exponential
        bares.decayCurve shouldBe AdsrCurve.Exponential
        bares.releaseCurve shouldBe AdsrCurve.Exponential

        // an unrecognized name coerces to the default, not to Square — on ALL three stages
        val typo = eval("""Osc.saw().adsr(0.01, 0.1, 0.7, 0.3).adsrCurve("sqare")""") as IgnitorDsl.Adsr
        typo.attackCurve shouldBe AdsrCurve.Exponential
        typo.decayCurve shouldBe AdsrCurve.Exponential
        typo.releaseCurve shouldBe AdsrCurve.Exponential

        // explicit names still win
        val explicit = eval("""Osc.saw().adsr(0.01, 0.1, 0.7, 0.3).adsrCurves("square", "cube", "linear")""") as IgnitorDsl.Adsr
        explicit.attackCurve shouldBe AdsrCurve.Square
        explicit.decayCurve shouldBe AdsrCurve.Cube
        explicit.releaseCurve shouldBe AdsrCurve.Linear
    }
})
