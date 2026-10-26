/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/** Redraws the host below, as the song page redraws on every cycle while a song plays. */
private val ticks = StreamSource(0)

/** A value knob held by its parent, as on the song page: every change redraws the parent with the new value. */
private class HeldHost(ctx: NoProps) : PureComponent(ctx) {
    private var rpm: Double by value(37.0)

    override fun VDom.render() {
        KlangValueKnob(
            name = "Revolutions per minute",
            value = rpm,
            range = 10.0..120.0,
            step = 0.5,
            onChange = { rpm = it },
            unit = "RPM",
            decimals = 2,
            perPixel = 0.05,
        )
    }
}

@Suppress("FunctionName")
private fun Tag.HeldHost() = comp { HeldHost(it) }

/** A value knob whose parent redraws on every tick, with a new onChange each time. */
private class TickingHost(ctx: NoProps) : PureComponent(ctx) {
    private val tick by subscribingTo(ticks)

    /** The tick of the last change; only there so the onChange below captures something. */
    private var taken = -1

    override fun VDom.render() {
        div {
            attributes["data-tick"] = tick.toString()

            KlangValueKnob(
                name = "Revolutions per minute",
                value = 37.0,
                range = 10.0..120.0,
                step = 0.5,
                // Captures the tick: a new lambda on every redraw, as the song page's are, so the knob redraws too
                onChange = { taken = tick },
                unit = "RPM",
                decimals = 2,
            )
        }
    }
}

@Suppress("FunctionName")
private fun Tag.TickingHost() = comp { TickingHost(it) }

/** The value knob (KLANG-00349): its reading, the knob's steps, typing a value on the LCD. */
class KlangValueKnobSpec : StringSpec({

    val styles = KlangControlStyles

    fun Element.q(selector: String): HTMLElement? = querySelector(selector) as HTMLElement?

    fun HTMLElement.key(key: String) {
        dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = key, bubbles = true, cancelable = true)))
    }

    suspend fun withKnob(value: Double, test: suspend (root: Element, changes: List<Double>) -> Unit) {
        val changes = mutableListOf<Double>()

        TestBed.preact(
            view = {
                KlangValueKnob(
                    name = "Revolutions per minute",
                    value = value,
                    range = 10.0..120.0,
                    step = 0.5,
                    onChange = { changes.add(it) },
                    unit = "RPM",
                    decimals = 2,
                    perPixel = 0.05,
                )
            },
        ) { root ->
            test(root[0], changes)
        }
    }

    "the LCD reads the value with its decimals and its unit; the knob stands where the value lies" {
        withKnob(value = 37.2) { root, _ ->
            root.q(".${styles.lcdValue}")!!.textContent shouldBe "37.20"
            root.q(".${styles.lcdUnit}")!!.textContent shouldBe "RPM"
            // (37.2 - 10) / 110 of the way, and said as the reading
            root.q("[role=slider]")!!.getAttribute("aria-valuenow") shouldBe "25"
            root.q("[role=slider]")!!.getAttribute("aria-valuetext") shouldBe "37.20 RPM"
        }

        KlangValueKnob.format(30.0, decimals = 0) shouldBe "30"
        KlangValueKnob.format(37.26, decimals = 1) shouldBe "37.3"
        KlangValueKnob.format(-2.5, decimals = 2) shouldBe "-2.50"
    }

    "a drag changes the value by its amount per pixel: 20 pixels up, one RPM" {
        withKnob(value = 37.0) { root, changes ->
            val dial = root.q("[role=slider]")!!

            fun pointer(type: String, clientY: Int) {
                dial.dispatchEvent(
                    PointerEvent(
                        type,
                        PointerEventInit(pointerId = 1, isPrimary = true, clientY = clientY, buttons = 1, bubbles = true),
                    )
                )
            }

            pointer("pointerdown", clientY = 300)
            pointer("pointermove", clientY = 280)

            changes shouldContainExactly listOf(38.0)
        }
    }

    "an arrow key on the knob steps the value by the step" {
        withKnob(value = 37.0) { root, changes ->
            root.q("[role=slider]")!!.key("ArrowUp")
            root.q("[role=slider]")!!.key("ArrowDown")

            changes shouldContainExactly listOf(37.5, 36.5)
        }
    }

    "a click on the LCD lets one type a value: Enter takes it, held to the decimals and kept in the range" {
        val cases = listOf("42.25" to 42.25, "42.333" to 42.33, "37.125" to 37.13, "500" to 120.0, "7,0" to 10.0)

        for ((typed, taken) in cases) {
            withKnob(value = 37.0) { root, changes ->
                root.q(".${styles.lcdValue}")!!.click()

                eventually { root.q("input") shouldNotBe null }

                val field = root.q("input") as HTMLInputElement

                field.value shouldBe "37.00"
                // Room for the widest value, "120.00": no digit scrolls out of the field
                field.style.width shouldBe "6ch"

                field.value = typed
                field.key("Enter")

                changes shouldContainExactly listOf(taken)

                eventually { root.q("input") shouldBe null }

                // The focus back on the reading, not lost to the page
                eventually { (document.activeElement as? Element)?.getAttribute("role") shouldBe "button" }
            }
        }
    }

    "a value typed, then the knob grabbed: the drag turns from the typed value, by the distance since the press" {
        TestBed.preact(view = { HeldHost() }) { root ->
            fun reading() = root[0].q(".${styles.lcdValue}")?.textContent

            root[0].q(".${styles.lcdValue}")!!.click()

            eventually { root[0].q("input") shouldNotBe null }

            (root[0].q("input") as HTMLInputElement).value = "50"

            val dial = root[0].q("[role=slider]")!!

            fun pointer(type: String, clientY: Int) {
                dial.dispatchEvent(
                    PointerEvent(
                        type,
                        PointerEventInit(pointerId = 1, isPrimary = true, clientY = clientY, buttons = 1, bubbles = true),
                    )
                )
            }

            // The press takes the focus from the field, which takes the typed 50
            pointer("pointerdown", clientY = 300)

            eventually { reading() shouldBe "50.00" }

            pointer("pointermove", clientY = 280)

            eventually { reading() shouldBe "51.00" }

            // The distance counts from the press, onto the value the drag started from
            pointer("pointermove", clientY = 260)

            eventually { reading() shouldBe "52.00" }
        }
    }

    "Space on the reading opens it too" {
        withKnob(value = 37.0) { root, _ ->
            root.q(".${styles.lcdValue}")!!.key(" ")

            eventually { root.q("input") shouldNotBe null }
        }
    }

    "what is typed stays while the page redraws: the field takes the value only when it opens" {
        TestBed.preact(view = { TickingHost() }) { root ->
            val reading = root[0].q(".${styles.lcdValue}")!!

            reading.click()

            eventually { root[0].q("input") shouldNotBe null }

            val field = root[0].q("input") as HTMLInputElement

            field.value = "4"
            ticks(ticks() + 1)

            eventually { root[0].q("[data-tick='${ticks()}']") shouldNotBe null }

            // The field on the screen now, not the element held from before
            (root[0].q("input") as HTMLInputElement).value shouldBe "4"
        }
    }

    "Esc gives the typing up, and what is not a number changes nothing" {
        for ((typed, key) in listOf("50" to "Escape", "fast" to "Enter")) {
            withKnob(value = 37.0) { root, changes ->
                root.q(".${styles.lcdValue}")!!.click()

                eventually { root.q("input") shouldNotBe null }

                val field = root.q("input") as HTMLInputElement

                field.value = typed
                field.key(key)

                eventually { root.q("input") shouldBe null }

                changes shouldBe emptyList()
                root.q(".${styles.lcdValue}")!!.textContent shouldBe "37.00"
            }
        }
    }
})
