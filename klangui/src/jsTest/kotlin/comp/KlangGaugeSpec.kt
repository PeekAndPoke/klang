/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.classes
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.css.px
import kotlinx.html.Tag
import org.w3c.dom.Element
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/** A gauge whose value a test sets, to see the needle follow new props. */
private class GaugeHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: GaugeHost? = null
        var smoothing: Double = 0.0
        var hotFrom: Double? = null
    }

    var reading: Double? by value(0.0)

    init {
        current = this
    }

    override fun VDom.render() {
        KlangGauge(name = "Host", value = reading, range = 0.0..1.0, smoothing = smoothing, hotFrom = hotFrom)
    }
}

@Suppress("FunctionName")
private fun Tag.GaugeHost() = comp { GaugeHost(it) }

/** The gauge (KLANG-00338): a meter, its offline, online and hot states, the needle and the red zone. */
class KlangGaugeSpec : StringSpec({

    val styles = KlangControlStyles

    fun needle(root: Element): Pair<Double, Double> {
        val line = root.querySelector("line[data-needle]")!!

        return line.getAttribute("x2")!!.toDouble() to line.getAttribute("y2")!!.toDouble()
    }

    "an online gauge is a meter with its name, range, raw value and value text" {
        TestBed.preact(
            view = {
                KlangGauge(name = "Motor heat", value = 0.38, display = { "${(it * 100).toInt()}%" })
            },
        ) { root ->
            val meter = root.selectCss("[role=meter]")

            meter.attr("aria-label") shouldContainExactly listOf("Motor heat")
            meter.attr("aria-valuemin") shouldContainExactly listOf("0")
            meter.attr("aria-valuemax") shouldContainExactly listOf("1")
            meter.attr("aria-valuenow") shouldContainExactly listOf("0.38")
            meter.attr("aria-valuetext") shouldContainExactly listOf("38%")
            meter.attr("data-gauge") shouldContainExactly listOf("online")
            (styles.gaugeOnline.toString() in meter.classes().single()) shouldBe true
            root.selectCss(".${styles.gaugeValue}").textContent() shouldBe "38%"
        }
    }

    "without a value the gauge is offline: no glow, no arc, no value, the needle at rest" {
        TestBed.preact(view = { KlangGauge(name = "Heat", value = null, display = { "$it" }) }) { root ->
            val meter = root.selectCss("[role=meter]")

            meter.attr("data-gauge") shouldContainExactly listOf("offline")
            meter.attr("aria-valuenow") shouldContainExactly listOf("0")
            meter.attr("aria-valuetext") shouldContainExactly listOf("offline")
            (styles.gaugeOnline.toString() in meter.classes().single()) shouldBe false
            root.selectCss(".${styles.gaugeArc}").size shouldBe 0
            root.selectCss(".${styles.gaugeValue}").size shouldBe 0

            val (x, y) = needle(root.single())

            x shouldBeLessThan 50.0
            y shouldBeGreaterThan 50.0
        }
    }

    "the needle points bottom left at the range's start, up in its middle, bottom right at its end" {
        TestBed.preact(
            view = {
                KlangGauge(name = "a", value = 10.0, range = 10.0..20.0)
                KlangGauge(name = "b", value = 15.0, range = 10.0..20.0)
                KlangGauge(name = "c", value = 20.0, range = 10.0..20.0)
                KlangGauge(name = "d", value = 99.0, range = 10.0..20.0)
            },
        ) { root ->
            val needles = root.selectCss("[role=meter]").map { needle(it) }

            needles[0].first shouldBeLessThan 50.0
            needles[0].second shouldBeGreaterThan 50.0

            needles[1].first shouldBe (50.0 plusOrMinus 1e-6)
            needles[1].second shouldBeLessThan 50.0

            needles[2].first shouldBeGreaterThan 50.0
            needles[2].second shouldBeGreaterThan 50.0

            // Past the range the needle stops at its end
            needles[3] shouldBe needles[2]
        }
    }

    "at or past hotFrom the gauge is hot, and the ticks from hotFrom on are red" {
        TestBed.preact(
            view = {
                KlangGauge(name = "a", value = 0.84, hotFrom = 0.85)
                KlangGauge(name = "b", value = 0.85, hotFrom = 0.85)
            },
        ) { root ->
            val meters = root.selectCss("[role=meter]")

            meters.attr("data-gauge") shouldContainExactly listOf("online", "hot")
            (styles.gaugeHot.toString() in meters.classes()[1]) shouldBe true
            (styles.gaugeArcHot.toString() in root.selectCss(".${styles.gaugeArc}").classes()[1]) shouldBe true

            // Eleven ticks a tenth apart: 0.9 and 1.0 lie at or past 0.85
            val hotTicks = meters.map { it.querySelectorAll(".${styles.gaugeTickHot}").length }

            hotTicks shouldContainExactly listOf(2, 2)
        }
    }

    "hot is decided in the range's units: a hotFrom past the end is never reached, one before the start always is" {
        TestBed.preact(
            view = {
                KlangGauge(name = "a", value = 1.0, hotFrom = 1.2)
                KlangGauge(name = "b", value = 12.0, range = 10.0..20.0, hotFrom = 5.0)
            },
        ) { root ->
            val meters = root.selectCss("[role=meter]")

            meters.attr("data-gauge") shouldContainExactly listOf("online", "hot")
            meters.map { it.querySelectorAll(".${styles.gaugeTickHot}").length } shouldContainExactly listOf(0, 11)
        }
    }

    "an online meter announces its reading held inside the range, its text the real one" {
        TestBed.preact(
            view = { KlangGauge(name = "a", value = 99.0, range = 10.0..20.0, display = { "${it.toInt()} units" }) },
        ) { root ->
            val meter = root.selectCss("[role=meter]")

            meter.attr("aria-valuenow") shouldContainExactly listOf("20")
            meter.attr("aria-valuetext") shouldContainExactly listOf("99 units")
        }
    }

    "a gauge without hotFrom never turns hot and has no red ticks" {
        TestBed.preact(view = { KlangGauge(name = "a", value = 1.0) }) { root ->
            root.selectCss("[role=meter]").attr("data-gauge") shouldContainExactly listOf("online")
            root.selectCss(".${styles.gaugeTickHot}").size shouldBe 0
            root.selectCss(".${styles.gaugeTick}").size shouldBe 11
        }
    }

    "a text label is printed instead of the icon" {
        TestBed.preact(
            view = {
                KlangGauge(name = "a", value = 0.5, label = "HEAT", icon = { microchip })
                KlangGauge(name = "b", value = 0.5, icon = { microchip }, size = 50.px)
            },
        ) { root ->
            val meters = root.selectCss("[role=meter]")

            meters[0].textContent shouldBe "HEAT"
            meters[0].querySelectorAll("i.icon").length shouldBe 0
            meters[1].querySelectorAll("i.icon.microchip").length shouldBe 1
        }
    }

    "a NaN or an infinite reading counts as none, and the next good reading moves the needle again" {
        GaugeHost.smoothing = 0.0

        TestBed.preact(view = { GaugeHost() }) { root ->
            val host = GaugeHost.current!!

            for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
                host.reading = bad

                eventually {
                    root.selectCss("[role=meter]").attr("data-gauge") shouldContainExactly listOf("offline")
                }

                host.reading = 1.0

                eventually {
                    root.selectCss("[role=meter]").attr("data-gauge") shouldContainExactly listOf("online")

                    val (x, y) = needle(root.single())

                    x shouldBeGreaterThan 50.0
                    y shouldBeGreaterThan 50.0
                }

                host.reading = 0.0
            }
        }
    }

    "going offline puts the needle to rest at once, smoothing or not" {
        GaugeHost.smoothing = 0.9

        TestBed.preact(view = { GaugeHost() }) { root ->
            val host = GaugeHost.current!!

            host.reading = 1.0

            // A tenth of the way: the needle has left its rest, upwards (at rest y is 71.2)
            eventually {
                needle(root.single()).second shouldBeLessThan 71.0
            }

            host.reading = null

            eventually {
                val (x, y) = needle(root.single())

                // The sweep's start, 30 from the centre at 135 degrees
                x shouldBe (28.787 plusOrMinus 1e-3)
                y shouldBe (71.213 plusOrMinus 1e-3)
            }
        }
    }

    "a range without a span, or a hotFrom that is not a number, never turns hot" {
        TestBed.preact(
            view = {
                KlangGauge(name = "a", value = 5.0, range = 5.0..5.0, hotFrom = 4.0)
                KlangGauge(name = "b", value = 0.5, hotFrom = Double.NaN)
            },
        ) { root ->
            root.selectCss("[role=meter]").attr("data-gauge") shouldContainExactly listOf("online", "online")
            root.selectCss(".${styles.gaugeTickHot}").size shouldBe 0
        }
    }

    "with smoothing the needle glides to a reading by itself, also while the reading holds still; without it it jumps" {
        // The end of the sweep, where a reading of 1 points the needle
        val (endX, endY) = Dial.point(fraction = 1.0, radius = 30.0)

        for (smoothing in listOf(0.9, 0.0)) {
            GaugeHost.smoothing = smoothing
            GaugeHost.hotFrom = null

            TestBed.preact(view = { GaugeHost() }) { root ->
                // One change of the reading, then no new props at all
                GaugeHost.current!!.reading = 1.0

                if (smoothing > 0.0) {
                    nextFrame()
                    nextFrame()
                    nextFrame()

                    val (x, y) = needle(root.single())

                    // On its way: a few frames in, not at the end yet
                    (abs(x - endX) + abs(y - endY)) shouldBeGreaterThan 1.0
                }

                eventually(timeout = 5.seconds) {
                    val (x, y) = needle(root.single())

                    x shouldBe (endX plusOrMinus 0.01)
                    y shouldBe (endY plusOrMinus 0.01)
                }
            }
        }
    }

    "the colour follows the needle: a reading past the red zone's start turns the gauge hot only once the needle is there" {
        GaugeHost.smoothing = 0.9
        GaugeHost.hotFrom = 0.85

        try {
            TestBed.preact(view = { GaugeHost() }) { root ->
                GaugeHost.current!!.reading = 1.0

                nextFrame()
                nextFrame()

                // The reading is past 0.85, the needle not yet
                root.single().querySelector("[role=meter]")!!.getAttribute("data-gauge") shouldBe "online"

                eventually(timeout = 5.seconds) {
                    root.single().querySelector("[role=meter]")!!.getAttribute("data-gauge") shouldBe "hot"
                }
            }
        } finally {
            GaugeHost.hotFrom = null
        }
    }
})
