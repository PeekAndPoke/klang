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
import kotlinx.browser.window
import kotlinx.css.px
import kotlinx.html.Tag
import org.w3c.dom.Element
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A gauge whose value a test sets, to see the needle follow new props. */
private class GaugeHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: GaugeHost? = null
    }

    var reading: Double? by value(0.0)

    init {
        current = this
    }

    override fun VDom.render() {
        KlangGauge(name = "Host", value = reading, range = 0.0..1.0)
    }
}

@Suppress("FunctionName")
private fun Tag.GaugeHost() = comp { GaugeHost(it) }

/** The gauge (KLANG-00338): a meter, its offline, online and hot states, the needle and the red zone. */
class KlangGaugeSpec : StringSpec({

    val styles = KlangControlStyles

    /** Where the needle's tip is: drawn at the sweep's start, turned round the centre by its `rotate`. */
    fun needle(root: Element): Pair<Double, Double> {
        val line = root.querySelector("line[data-needle]")!!
        val x = line.getAttribute("x2")!!.toDouble() - 50.0
        val y = line.getAttribute("y2")!!.toDouble() - 50.0
        val degrees = Regex("""rotate\(([-0-9.e]+)""").find(line.getAttribute("transform") ?: "")
            ?.groupValues?.get(1)?.toDouble() ?: 0.0
        val radians = degrees * PI / 180.0

        return (50.0 + x * cos(radians) - y * sin(radians)) to (50.0 + x * sin(radians) + y * cos(radians))
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

    "the gauge's reading cannot be selected: an instrument, not text" {
        TestBed.preact(view = { KlangGauge(name = "Motor heat", value = 0.5, display = { "50%" }) }) { root ->
            val value = root.selectCss(".${styles.gaugeValue}").single()

            window.getComputedStyle(value).getPropertyValue("user-select") shouldBe "none"
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

    "going offline puts the needle to rest at once" {
        TestBed.preact(view = { GaugeHost() }) { root ->
            val host = GaugeHost.current!!

            // Half way: the needle points up, away from its rest (at rest y is 71.2)
            host.reading = 0.5

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

    "the icon is printed on the face under the needle: it comes before the scale, which carries the needle" {
        TestBed.preact(view = { KlangGauge(name = "Heat", value = 0.5, icon = { microchip }) }) { root ->
            val gauge = root.single().querySelector("[role=meter]")!!
            val parts = (0 until gauge.children.length).map { gauge.children.item(it)!!.className }
            val mark = parts.indexOfFirst { styles.gaugeMark.toString() in it }
            val scale = parts.indexOfFirst { styles.gaugeScale.toString() in it }

            (mark in 0 until scale) shouldBe true
        }
    }
})
