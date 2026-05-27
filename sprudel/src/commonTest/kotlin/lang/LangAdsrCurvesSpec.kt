package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangAdsrCurvesSpec : StringSpec({

    "adsrCurves dsl interface — sets per-stage curves from 'a:d:r'" {
        val pat = "0 1"
        val ctrl = "linear:square:cube"

        dslInterfaceTests(
            "pattern.adsrCurves(ctrl)" to
                    seq(pat).adsrCurves(ctrl),
            "script pattern.adsrCurves(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").adsrCurves("$ctrl")"""),
            "string.adsrCurves(ctrl)" to
                    pat.adsrCurves(ctrl),
            "script string.adsrCurves(ctrl)" to
                    SprudelPattern.compile(""""$pat".adsrCurves("$ctrl")"""),
            "adsrCurves(ctrl)" to
                    seq(pat).apply(adsrCurves(ctrl)),
            "script adsrCurves(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(adsrCurves("$ctrl"))"""),
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
        val p = "0 1".apply(adsrCurves(":square:cube"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        with(events[0].data) {
            attackCurve shouldBe null    // empty first part — left null
            decayCurve shouldBe AdsrCurve.Square
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurves() accepts case-insensitive names" {
        val p = "0".apply(adsrCurves("LINEAR:Square:CUBE"))
        val events = p.queryArc(0.0, 1.0)
        with(events[0].data) {
            attackCurve shouldBe AdsrCurve.Linear
            decayCurve shouldBe AdsrCurve.Square
            releaseCurve shouldBe AdsrCurve.Cube
        }
    }

    "adsrCurves() invalid name leaves stage untouched" {
        val p = "0".apply(adsrCurves("xyz:square:cube"))
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
