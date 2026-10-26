/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.selectCss
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** The ignition key (KLANG-00347): one turn at OFF, none past it; where it points; its beacon; its glow lamp. */
class KlangIgnitionSpec : StringSpec({

    val styles = KlangControlStyles

    fun Element.cap(): HTMLElement = querySelector("button") as HTMLElement

    fun Element.turn(): String = (querySelector("[data-key-turn]") as HTMLElement).style.transform

    fun Element.lamp(): String? = querySelector("[data-ignition-label=On] [data-lamp]")?.getAttribute("data-lamp")

    "at OFF a click asks to turn the key, once per click; the key waits with the beacon" {
        var turns = 0

        TestBed.preact(
            view = { KlangIgnition(position = KlangIgnition.Position.Off, glowing = false, onTurn = { turns++ }) },
        ) { root ->
            val key = root[0]

            key.cap().getAttribute("aria-label") shouldBe "Start the Motor: off"
            key.cap().getAttribute("aria-disabled") shouldBe "false"
            (styles.beacon.toString() in key.cap().className.split(" ")) shouldBe true

            key.cap().click()
            key.cap().click()

            turns shouldBe 2
        }
    }

    "past OFF a click turns nothing; the key keeps its focus, without the beacon" {
        for (position in listOf(KlangIgnition.Position.On, KlangIgnition.Position.Start)) {
            var turns = 0

            TestBed.preact(view = { KlangIgnition(position = position, glowing = false, onTurn = { turns++ }) }) { root ->
                val key = root[0]

                key.cap().getAttribute("aria-disabled") shouldBe "true"
                // Still a button that takes the focus: aria-disabled, not disabled
                key.cap().hasAttribute("disabled") shouldBe false
                (styles.beacon.toString() in key.cap().className.split(" ")) shouldBe false

                key.cap().click()

                turns shouldBe 0
            }
        }
    }

    "the key points to its position: OFF upper left, ON up, START upper right; the position is printed bold" {
        TestBed.preact(
            view = {
                KlangIgnition(position = KlangIgnition.Position.Off, glowing = false, onTurn = {})
                KlangIgnition(position = KlangIgnition.Position.On, glowing = false, onTurn = {})
                KlangIgnition(position = KlangIgnition.Position.Start, glowing = false, onTurn = {})
            },
        ) { root ->
            val keys = root.selectCss(".${styles.ignition}")

            keys.map { it.turn() } shouldContainExactly listOf("rotate(-45deg)", "rotate(0deg)", "rotate(45deg)")

            keys.map { key ->
                key.querySelector(".${styles.ignitionLabelCurrent}")?.getAttribute("data-ignition-label")
            } shouldContainExactly listOf("Off", "On", "Start")
        }
    }

    "the glow lamp beside ON burns amber while glowing, and is dark otherwise; the key's name says so too" {
        TestBed.preact(
            view = {
                KlangIgnition(position = KlangIgnition.Position.On, glowing = true, onTurn = {})
                KlangIgnition(position = KlangIgnition.Position.On, glowing = false, onTurn = {})
                KlangIgnition(position = KlangIgnition.Position.Start, glowing = false, onTurn = {})
            },
        ) { root ->
            val keys = root.selectCss(".${styles.ignition}")

            keys.take(2).map { it.lamp() } shouldContainExactly listOf("Warning", "Off")
            keys.map { it.cap().getAttribute("aria-label") } shouldContainExactly listOf(
                "Start the Motor: on, warming up",
                "Start the Motor: on",
                "Start the Motor: running",
            )
        }
    }
})
