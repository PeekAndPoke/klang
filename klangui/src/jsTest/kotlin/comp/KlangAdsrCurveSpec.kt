/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.Element
import kotlin.math.exp

/** The time a spec sets, and the frames it runs by hand. */
private class TestClock(var now: Double) : KlangAdsrCurve.Clock {
    private val waiting = mutableListOf<() -> Unit>()

    /** Frames asked for and not run yet. */
    val pending: Int get() = waiting.size

    override fun nowSec(): Double = now

    override fun nextFrame(onFrame: () -> Unit) {
        waiting.add(onFrame)
    }

    /** Moves the time to [sec] and runs the frame waiting, as the browser would. */
    fun frameAt(sec: Double) {
        now = sec
        val run = waiting.toList()
        waiting.clear()
        run.forEach { it() }
    }
}

/** A host that shows a curve until told not to: its unmount, on demand. */
private class CurveHost(ctx: Ctx<Props>) : Component<CurveHost.Props>(ctx) {
    data class Props(val signals: StreamSource<KlangAdsrSignal?>, val clock: KlangAdsrCurve.Clock)

    companion object {
        var current: CurveHost? = null
    }

    var shown: Boolean by value(true)

    init {
        current = this
    }

    override fun VDom.render() {
        div {
            if (shown) {
                KlangAdsrCurve(
                    adsr = KlangAdsr(attack = 0.2, decay = 0.1, sustain = 0.5, release = 0.4),
                    signals = props.signals,
                    clock = props.clock,
                )
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.CurveHost(signals: StreamSource<KlangAdsrSignal?>, clock: KlangAdsrCurve.Clock) =
    comp(CurveHost.Props(signals = signals, clock = clock)) { CurveHost(it) }

/** The curve that follows the notes (KLANG-00371): a light per note along the curve, the shape that plays, the dim. */
class KlangAdsrCurveSpec : StringSpec({

    val written = KlangAdsr(attack = 0.2, decay = 0.1, sustain = 0.5, release = 0.4)
    val corners = KlangAdsrGeometry(written).points

    fun Element.lights(): List<KlangAdsrPoint> = querySelectorAll("circle").let { list ->
        (0 until list.length).map { index ->
            val circle = list.item(index) as Element

            KlangAdsrPoint(circle.getAttribute("cx")!!.toDouble(), circle.getAttribute("cy")!!.toDouble())
        }
    }

    fun KlangAdsrPoint.shouldBeAt(x: Double, y: Double) {
        this.x shouldBe (x plusOrMinus 0.02)
        this.y shouldBe (y plusOrMinus 0.02)
    }

    fun between(a: KlangAdsrPoint, b: KlangAdsrPoint, f: Double) = KlangAdsrPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

    "each curve runs from 0 to 1, in the engine's shapes" {
        for (curve in AdsrCurve.entries) {
            curve.drawnAt(0.0) shouldBe (0.0 plusOrMinus 1e-12)
            curve.drawnAt(1.0) shouldBe (1.0 plusOrMinus 1e-12)
        }

        AdsrCurve.Linear.drawnAt(0.5) shouldBe 0.5
        AdsrCurve.Square.drawnAt(0.5) shouldBe 0.25
        AdsrCurve.Cube.drawnAt(0.5) shouldBe 0.125
        AdsrCurve.SCurve.drawnAt(0.25) shouldBe 0.125
        AdsrCurve.SCurve.drawnAt(0.75) shouldBe 0.875
        AdsrCurve.InvSquare.drawnAt(0.5) shouldBe 0.75
        AdsrCurve.Exponential.drawnAt(0.5) shouldBe ((exp(ADSR_EXP_K * 0.5) - 1.0) / (exp(ADSR_EXP_K) - 1.0) plusOrMinus 1e-12)
    }

    "the curve as drawn: each stage's points where the engine's law puts them, written out by hand" {
        val bent = written.copy(attackCurve = AdsrCurve.Exponential, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.InvSquare)
        val drawn = KlangAdsrGeometry(bent).curve
        fun yOf(level: Double) = KlangAdsrGeometry.PAD_TOP + (1.0 - level) * KlangAdsrGeometry.DRAW_HEIGHT

        // A quarter into each stage (6 of 24 pieces): attack f(0.25); decay s + (1 - s) f(0.75); release s f(0.75)
        drawn[6].y shouldBe (yOf((exp(ADSR_EXP_K * 0.25) - 1.0) / (exp(ADSR_EXP_K) - 1.0)) plusOrMinus 1e-9)
        drawn[24 + 6].y shouldBe (yOf(0.5 + 0.5 * (0.75 * 0.75)) plusOrMinus 1e-9)
        drawn[24 + 24 + 1 + 6].y shouldBe (yOf(0.5 * (0.75 * (2.0 - 0.75))) plusOrMinus 1e-9)
    }

    "a curved stage: the light keeps its time across, and its height follows the curve" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)
        val bent = written.copy(attackCurve = AdsrCurve.Exponential, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.InvSquare)
        val at = KlangAdsrGeometry(bent).points

        TestBed.preact(view = { KlangAdsrCurve(adsr = bent, signals = signals, clock = clock) }) { root ->
            val view = root.single()

            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = bent))

            // Halfway through the attack: halfway across, but only the exponential's share of the way up
            clock.frameAt(10.1)
            nextFrame()
            view.lights().single().shouldBeAt(
                x = (at[0].x + at[1].x) / 2,
                y = at[0].y + (at[1].y - at[0].y) * AdsrCurve.Exponential.drawnAt(0.5),
            )

            // A quarter into the decay (it runs 10.2 to 10.3): from full towards the sustain along the square, written
            // out: s + (1 - s) * 0.75^2, at an uneven point so a curve turned around would show
            clock.frameAt(10.225)
            nextFrame()
            view.lights().single().shouldBeAt(
                x = at[1].x + (at[2].x - at[1].x) * 0.25,
                y = KlangAdsrGeometry.PAD_TOP + (1.0 - (0.5 + 0.5 * 0.75 * 0.75)) * KlangAdsrGeometry.DRAW_HEIGHT,
            )

            // A quarter into the release from the sustain: the sustain times the curve of the three quarters left
            clock.frameAt(11.1)
            nextFrame()
            view.lights().single().shouldBeAt(
                x = at[3].x + (at[4].x - at[3].x) * 0.25,
                y = KlangAdsrGeometry.PAD_TOP + (1.0 - 0.5 * (0.75 * (2.0 - 0.75))) * KlangAdsrGeometry.DRAW_HEIGHT,
            )
        }
    }

    "before any note: the written shape, dimmed, no light" {
        val signals = StreamSource<KlangAdsrSignal?>(null)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = TestClock(now = 100.0)) }) { root ->
            val view = root.single()

            view.querySelector("polyline")!!.getAttribute("points") shouldBe KlangAdsrGeometry(written).polylinePoints()
            view.querySelector("[data-lit]")!!.getAttribute("data-lit") shouldBe "false"
            view.lights().size shouldBe 0
        }
    }

    "a note's light climbs the attack, crosses the sustain while held, runs down the release, then goes" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()

            // Starts at 10, its gate open for a second; attack 0.2, decay 0.1, release 0.4
            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = written))

            suspend fun lightAt(sec: Double): List<KlangAdsrPoint> {
                clock.frameAt(sec)
                nextFrame()

                return view.lights()
            }

            lightAt(9.5).size shouldBe 0
            view.querySelector("[data-lit]")!!.getAttribute("data-lit") shouldBe "false"

            lightAt(10.1).single().let { at -> between(corners[0], corners[1], 0.5).let { at.shouldBeAt(it.x, it.y) } }
            view.querySelector("[data-lit]")!!.getAttribute("data-lit") shouldBe "true"

            lightAt(10.25).single().let { at -> between(corners[1], corners[2], 0.5).let { at.shouldBeAt(it.x, it.y) } }

            // Held from 10.3 to 11: at 10.65 half the plateau crossed
            lightAt(10.65).single().let { at -> between(corners[2], corners[3], 0.5).let { at.shouldBeAt(it.x, it.y) } }

            lightAt(11.2).single().let { at -> between(corners[3], corners[4], 0.5).let { at.shouldBeAt(it.x, it.y) } }

            lightAt(11.5).size shouldBe 0
        }
    }

    "a note shorter than its attack and decay releases from where its light is" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()

            // The gate closes at 10.1, halfway up the attack; the release runs from there to the curve's end
            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 10.1, adsr = written))

            clock.frameAt(10.3)
            nextFrame()

            val from = between(corners[0], corners[1], 0.5)

            view.lights().single().let { at -> between(from, corners[4], 0.5).let { at.shouldBeAt(it.x, it.y) } }
        }
    }

    "notes that overlap are lights at once; every note counts, an equal one too" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()
            val note = KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = written)

            signals(note)
            signals(note)
            signals(KlangAdsrSignal.Note(startSec = 10.5, gateEndSec = 11.0, adsr = written))

            clock.frameAt(10.6)
            nextFrame()

            view.lights().size shouldBe 3
        }
    }

    "a clear voids the notes ahead; the one sounding stays" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()

            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = written))
            signals(KlangAdsrSignal.Note(startSec = 10.8, gateEndSec = 11.5, adsr = written))

            clock.frameAt(10.5)
            signals(KlangAdsrSignal.Clear)

            clock.frameAt(10.9)
            nextFrame()

            view.lights().size shouldBe 1
        }
    }

    "after the last note the frames stop; the curve stays lit a second past its release, then dims" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()
            fun lit() = view.querySelector("[data-lit]")!!.getAttribute("data-lit")

            // Its release ends at 11.4
            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = written))

            clock.frameAt(11.5)
            nextFrame()
            lit() shouldBe "true"

            clock.frameAt(12.2)
            nextFrame()
            clock.frameAt(12.5)
            nextFrame()

            lit() shouldBe "false"
            clock.pending shouldBe 0
        }
    }

    "gone from the page, it asks for no more frames" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)

        TestBed.preact(view = { CurveHost(signals = signals, clock = clock) }) { _ ->
            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = written))
            clock.frameAt(10.2)
            nextFrame()
            clock.pending shouldBe 1

            CurveHost.current!!.shown = false
            nextFrame()

            clock.frameAt(10.4)
            clock.pending shouldBe 0
        }
    }

    "a note over already (the last one again, for a new subscriber) draws nothing" {
        val signals = StreamSource<KlangAdsrSignal?>(KlangAdsrSignal.Note(startSec = 1.0, gateEndSec = 2.0, adsr = written))
        val clock = TestClock(now = 50.0)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            clock.frameAt(50.1)
            nextFrame()

            root.single().lights().size shouldBe 0
        }
    }

    "a note brings the shape that plays: the curve takes it once the note begins" {
        val signals = StreamSource<KlangAdsrSignal?>(null)
        val clock = TestClock(now = 9.0)
        val edited = KlangAdsr(attack = 0.001, decay = 0.1, sustain = 1.0, release = 0.05)

        TestBed.preact(view = { KlangAdsrCurve(adsr = written, signals = signals, clock = clock) }) { root ->
            val view = root.single()
            fun drawn() = view.querySelector("polyline")!!.getAttribute("points")

            signals(KlangAdsrSignal.Note(startSec = 10.0, gateEndSec = 11.0, adsr = edited))

            // Scheduled ahead: the shape stays the written one until the note sounds
            clock.frameAt(9.5)
            nextFrame()
            drawn() shouldBe KlangAdsrGeometry(written).polylinePoints()

            clock.frameAt(10.2)
            nextFrame()
            drawn() shouldBe KlangAdsrGeometry(edited).polylinePoints()
            drawn() shouldNotBe KlangAdsrGeometry(written).polylinePoints()
        }
    }
})
