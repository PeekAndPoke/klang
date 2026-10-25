/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/** A knob held by its parent, as on the kitchen sink: every change redraws the parent with a new position. */
private class KnobHost(ctx: NoProps) : PureComponent(ctx) {
    var position: Double by value(0.5)

    override fun VDom.render() {
        KlangKnob(name = "held", value = position, onChange = { position = it })
    }
}

@Suppress("FunctionName")
private fun Tag.KnobHost() = comp { KnobHost(it) }

/** The knob (KLANG-00338): a slider for screen readers, turned by keys and by dragging, and its variants. */
class KlangKnobSpec : StringSpec({

    val styles = KlangControlStyles

    fun press(dial: Element, key: String): Boolean {
        val event = KeyboardEvent("keydown", KeyboardEventInit(key = key, bubbles = true, cancelable = true))

        dial.dispatchEvent(event)

        return event.defaultPrevented
    }

    fun pointer(dial: Element, type: String, clientY: Int, pointerId: Int = 1, button: Int = 0, buttons: Int = 1) {
        dial.dispatchEvent(
            PointerEvent(
                type,
                PointerEventInit(
                    pointerId = pointerId,
                    isPrimary = true,
                    clientY = clientY,
                    button = button.toShort(),
                    buttons = buttons.toShort(),
                    bubbles = true,
                    cancelable = true,
                ),
            )
        )
    }

    "a knob is a focusable slider with its name, position and value text" {
        TestBed.preact(view = { KlangKnob(name = "Cutoff", value = 0.62, onChange = {}, valueText = "8 kHz") }) { root ->
            val slider = root.selectCss("[role=slider]")

            slider.attr("tabindex") shouldContainExactly listOf("0")
            slider.attr("aria-label") shouldContainExactly listOf("Cutoff")
            slider.attr("aria-valuemin") shouldContainExactly listOf("0")
            slider.attr("aria-valuemax") shouldContainExactly listOf("100")
            slider.attr("aria-valuenow") shouldContainExactly listOf("62")
            slider.attr("aria-valuetext") shouldContainExactly listOf("8 kHz")
        }
    }

    "the position is announced rounded, not cut off" {
        TestBed.preact(
            view = {
                KlangKnob(name = "a", value = 0.29, onChange = {})
                KlangKnob(name = "b", value = 0.57, onChange = {})
            },
        ) { root ->
            root.selectCss("[role=slider]").attr("aria-valuenow") shouldContainExactly listOf("29", "57")
        }
    }

    "the arrow keys turn it by a step, Home and End to its ends; each press ends a change" {
        val changes = mutableListOf<Double>()
        var ends = 0

        TestBed.preact(
            view = { KlangKnob(name = "k", value = 0.5, onChange = { changes.add(it) }, onChangeEnd = { ends++ }, step = 0.1) },
        ) { root ->
            val dial = root.selectCss("[role=slider]").single()

            for (key in listOf("ArrowUp", "ArrowRight", "ArrowDown", "ArrowLeft", "Home", "End")) {
                press(dial, key) shouldBe true
            }

            changes.map { (it * 100).toInt() } shouldContainExactly listOf(60, 60, 40, 40, 0, 100)
            ends shouldBe 6
        }
    }

    "a key past an end stays at the end; another key does nothing and is not taken" {
        val changes = mutableListOf<Double>()

        TestBed.preact(
            view = {
                KlangKnob(name = "top", value = 1.0, onChange = { changes.add(it) })
                KlangKnob(name = "bottom", value = 0.0, onChange = { changes.add(it) })
            },
        ) { root ->
            val dials = root.selectCss("[role=slider]")

            press(dials[0], "ArrowUp")
            press(dials[1], "ArrowDown")

            changes shouldContainExactly listOf(1.0, 0.0)

            changes.clear()

            press(dials[0], "a") shouldBe false
            press(dials[0], "Enter") shouldBe false

            changes.shouldBeEmpty()
        }
    }

    "dragging up turns it up, 75 pixels for the whole way, held at the ends; the release ends the change" {
        val changes = mutableListOf<Double>()
        var ends = 0

        TestBed.preact(
            view = { KlangKnob(name = "k", value = 0.5, onChange = { changes.add(it) }, onChangeEnd = { ends++ }) },
        ) { root ->
            val dial = root.selectCss("[role=slider]").single()

            pointer(dial, "pointerdown", clientY = 300)
            pointer(dial, "pointermove", clientY = 285)
            pointer(dial, "pointermove", clientY = 330)
            pointer(dial, "pointermove", clientY = 100)

            changes.size shouldBe 3
            changes[0] shouldBe (0.7 plusOrMinus 1e-9)
            changes[1] shouldBe (0.1 plusOrMinus 1e-9)
            changes[2] shouldBe 1.0
            ends shouldBe 0

            pointer(dial, "pointerup", clientY = 100, buttons = 0)

            ends shouldBe 1

            // After the release a move turns nothing
            pointer(dial, "pointermove", clientY = 50)

            changes.size shouldBe 3
        }
    }

    "a drag of another pointer, or with another button, turns nothing" {
        val changes = mutableListOf<Double>()

        TestBed.preact(view = { KlangKnob(name = "k", value = 0.5, onChange = { changes.add(it) }) }) { root ->
            val dial = root.selectCss("[role=slider]").single()

            pointer(dial, "pointerdown", clientY = 300, button = 2, buttons = 2)
            pointer(dial, "pointermove", clientY = 200, buttons = 2)

            pointer(dial, "pointerdown", clientY = 300, pointerId = 1)
            pointer(dial, "pointermove", clientY = 200, pointerId = 7)

            changes.shouldBeEmpty()
        }
    }

    "a knob held by its parent keeps its focus and turns through the parent's redraws" {
        TestBed.preact(view = { KnobHost() }) { root ->
            suspend fun dial() = root.selectCss("[role=slider]").single() as HTMLElement

            pointer(dial(), "pointerdown", clientY = 300)
            pointer(dial(), "pointerup", clientY = 300, buttons = 0)

            // Kraft redraws after the event (Preact batches it): each key waits for the one before to land, as real
            // key repeats do
            press(dial(), "ArrowUp")
            eventually { dial().getAttribute("aria-valuenow") shouldBe "55" }

            press(dial(), "ArrowUp")

            eventually {
                dial().getAttribute("aria-valuenow") shouldBe "60"
                (dial().ownerDocument?.activeElement == dial()) shouldBe true
            }
        }
    }

    "a press on the dial focuses it, so the keys work after a click" {
        TestBed.preact(view = { KlangKnob(name = "k", value = 0.5, onChange = {}) }) { root ->
            val dial = root.selectCss("[role=slider]").single() as HTMLElement

            pointer(dial, "pointerdown", clientY = 300)

            (dial.ownerDocument?.activeElement == dial) shouldBe true
        }
    }

    "the cap turns from -135 to 135 degrees; a pointer without an icon, a dot with one" {
        TestBed.preact(
            view = {
                KlangKnob(name = "a", value = 0.0, onChange = {})
                KlangKnob(name = "b", value = 1.0, onChange = {}, icon = { volume_up })
            },
        ) { root ->
            val turns = root.selectCss("[data-knob-turn]").map { (it as HTMLElement).style.transform }

            turns shouldContainExactly listOf("rotate(-135deg)", "rotate(135deg)")

            val dials = root.selectCss("[role=slider]")

            dials[0].querySelectorAll(".${styles.knobPointer}").length shouldBe 1
            dials[0].querySelectorAll(".${styles.knobDot}").length shouldBe 0
            dials[1].querySelectorAll(".${styles.knobPointer}").length shouldBe 0
            dials[1].querySelectorAll(".${styles.knobDot}").length shouldBe 1
            dials[1].querySelectorAll("i.icon.volume.up").length shouldBe 1
        }
    }

    "the ring is drawn unless switched off, and its arc follows the position" {
        TestBed.preact(
            view = {
                KlangKnob(name = "a", value = 0.0, onChange = {})
                KlangKnob(name = "b", value = 0.5, onChange = {})
                KlangKnob(name = "c", value = 0.5, onChange = {}, ring = false)
            },
        ) { root ->
            val dials = root.selectCss("[role=slider]")

            dials.map { it.querySelectorAll("svg").length } shouldContainExactly listOf(1, 1, 0)
            dials[0].querySelector("[data-knob-arc]")!!.getAttribute("d") shouldBe ""
            // Worked out by hand: from 135 degrees on radius 45 to the top, a small arc
            dials[1].querySelector("[data-knob-arc]")!!.getAttribute("d") shouldBe "M 18.18 81.82 A 45 45 0 0 1 50.00 5.00"
        }
    }

    "above: the label, the dial, the value; below: the dial, then label and value as one caption" {
        TestBed.preact(
            view = {
                KlangKnob(
                    name = "a",
                    value = 0.5,
                    onChange = {},
                    label = "CUTOFF",
                    labelPosition = KlangKnob.LabelPosition.Above,
                    valueText = "8 kHz",
                )
                KlangKnob(name = "b", value = 0.5, onChange = {}, label = "VOLUME", valueText = "-6 dB")
                KlangKnob(name = "c", value = 0.5, onChange = {}, label = "PLAIN", labelPosition = KlangKnob.LabelPosition.Above)
            },
        ) { root ->
            val knobs = root.selectCss(".${styles.knob}")

            fun parts(i: Int): List<String> = (0 until knobs[i].children.length).map { k ->
                val child = knobs[i].children.item(k)!!

                when {
                    child.getAttribute("role") == "slider" -> "dial"
                    child.classList.contains(styles.knobCaption.toString()) -> "caption(${child.textContent})"
                    else -> child.textContent ?: ""
                }
            }

            parts(0) shouldContainExactly listOf("CUTOFF", "dial", "caption(8 kHz)")
            parts(1) shouldContainExactly listOf("dial", "caption(VOLUME-6 dB)")
            parts(2) shouldContainExactly listOf("PLAIN", "dial")
        }
    }
})
