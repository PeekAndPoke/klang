/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * ADSR curve default pin (maintainer decision, 2026-08-24): NOTHING SET means
 * [AdsrCurve.Default] (= Exponential) on every stage and every door. On the IGNITOR door,
 * a bare `curves()` (in the chain `adsr`'s lambda since step 3c) also means exp and an
 * unrecognized name coerces to exp.
 * (The SPRUDEL door deliberately differs on those two: a bare control call is a no-op and
 * a bad name keeps the prior curve — per-event control-pattern semantics, pinned by
 * `LangAdsrCurvesSpec`; only the UNSET default is shared across doors.) The ignitor door
 * used to default attack/release to Square while the strip wire resolved Exponential (one
 * knob, two sounds). This spec is the tripwire.
 */
class LangAdsrCurveDefaultSpec : StringSpec({

    "sprudel wire: an unset curve is the classic() curve slot's default, Exponential, on every stage" {
        // A pattern that writes no curve sends no `adsrCurves.*` key (next row), so the slot's default rules.
        val exp = AdsrCurves.indexOf(AdsrCurve.Exponential)

        (IgnitorDsl.Slots.adsrCurves.attack as IgnitorDsl.Param).default shouldBe exp
        (IgnitorDsl.Slots.adsrCurves.decay as IgnitorDsl.Param).default shouldBe exp
        (IgnitorDsl.Slots.adsrCurves.release as IgnitorDsl.Param).default shouldBe exp
    }

    "sprudel door: a pattern without curve calls carries NO curve on the wire (engine default rules)" {
        val data = SprudelPattern.compile("""note("c").adsr(0.01, 0.1, 0.7, 0.3)""")!!
            .queryArc(0.0, 1.0).first().data
        data.attackCurve shouldBe null
        data.decayCurve shouldBe null
        data.releaseCurve shouldBe null
    }

    "ignitor node: unset curves are the exp knob, and so are a bare curves() and an unknown name" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        // Since step 3c a curve is an INDEX knob on the node; its default is exp's index.
        val exp = AdsrCurves.knob(AdsrCurve.Exponential)

        // no call at all -> the node's default knob, exp
        val plain = eval("""Ignitor.saw().adsr(0.01, 0.1, 0.7, 0.3)""") as IgnitorDsl.Adsr
        plain.attackCurve shouldBe exp
        plain.decayCurve shouldBe exp
        plain.releaseCurve shouldBe exp

        // bare curves() -> Exponential on every stage
        val bare = eval("""Ignitor.saw().adsr(0.01, 0.1, 0.7, 0.3, e => e.curves())""") as IgnitorDsl.Adsr
        bare.attackCurve shouldBe exp
        bare.decayCurve shouldBe exp
        bare.releaseCurve shouldBe exp

        // an unrecognized name coerces to the default, not to Square, on ALL three stages
        val typo = eval("""Ignitor.saw().adsr(0.01, 0.1, 0.7, 0.3, e => e.curves("sqare", "sqare", "sqare"))""") as IgnitorDsl.Adsr
        typo.attackCurve shouldBe exp
        typo.decayCurve shouldBe exp
        typo.releaseCurve shouldBe exp

        // explicit names still win
        val explicit = eval("""Ignitor.saw().adsr(0.01, 0.1, 0.7, 0.3, e => e.curves("square", "cube", "linear"))""") as IgnitorDsl.Adsr
        explicit.attackCurve shouldBe AdsrCurves.knob(AdsrCurve.Square)
        explicit.decayCurve shouldBe AdsrCurves.knob(AdsrCurve.Cube)
        explicit.releaseCurve shouldBe AdsrCurves.knob(AdsrCurve.Linear)
    }
})
