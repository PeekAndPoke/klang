/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.classes
import io.peekandpoke.kraft.testing.click
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import org.w3c.dom.HTMLButtonElement

/** The Klang button (KLANG-00337): a real button, its classes per finish, surface and shape, and its states. */
class KlangButtonSpec : StringSpec({

    val styles = KlangControlStyles

    "a plain button is a type=button with its content, and a click calls onClick" {
        var clicks = 0

        TestBed.preact(view = { KlangButton(onClick = { clicks++ }) { +"Play" } }) { root ->
            val button = root.selectCss("button")

            button.attr("type") shouldContainExactly listOf("button")
            button.textContent() shouldBe "Play"
            button.attr("aria-pressed") shouldContainExactly listOf(null)

            button.click()
            button.click()

            clicks shouldBe 2
        }
    }

    "each finish and surface picks its own class" {
        val expected = mapOf(
            (KlangButton.Finish.Dark to KlangButton.Surface.Flat) to styles.darkFlat,
            (KlangButton.Finish.Dark to KlangButton.Surface.Domed) to styles.darkDomed,
            (KlangButton.Finish.Silver to KlangButton.Surface.Flat) to styles.silverFlat,
            (KlangButton.Finish.Silver to KlangButton.Surface.Domed) to styles.silverDomed,
        )

        for ((combo, cls) in expected) {
            val (finish, surface) = combo

            TestBed.preact(view = { KlangButton(onClick = {}, finish = finish, surface = surface) { +"x" } }) { root ->
                root.selectCss("button").classes().single() shouldBe setOf(styles.button.toString(), cls.toString())
            }
        }
    }

    "a shape adds its class; the rectangle adds none" {
        TestBed.preact(
            view = {
                KlangButton(onClick = {}) { +"rect" }
                KlangButton(onClick = {}, shape = KlangButton.Shape.Pill) { +"pill" }
                KlangButton(onClick = {}, shape = KlangButton.Shape.Round, ariaLabel = "Stop") { +"x" }
            },
        ) { root ->
            val classes = root.selectCss("button").classes()

            classes[0] shouldBe setOf(styles.button.toString(), styles.darkFlat.toString())
            classes[1] shouldBe setOf(styles.button.toString(), styles.darkFlat.toString(), styles.pill.toString())
            classes[2] shouldBe setOf(styles.button.toString(), styles.darkFlat.toString(), styles.round.toString())
            root.selectCss("button").attr("aria-label")[2] shouldBe "Stop"
        }
    }

    "a toggle says whether it is pressed, and a pressed cap takes its finish's pressed class" {
        TestBed.preact(
            view = {
                KlangButton(onClick = {}, pressed = true) { +"on" }
                KlangButton(onClick = {}, pressed = false) { +"off" }
                KlangButton(onClick = {}, pressed = true, finish = KlangButton.Finish.Silver) { +"silver on" }
            },
        ) { root ->
            val buttons = root.selectCss("button")

            buttons.attr("aria-pressed") shouldContainExactly listOf("true", "false", "true")
            (styles.pressedDark.toString() in buttons.classes()[0]) shouldBe true
            (styles.pressedDark.toString() in buttons.classes()[1]) shouldBe false
            (styles.pressedSilver.toString() in buttons.classes()[2]) shouldBe true
            (styles.pressedDark.toString() in buttons.classes()[2]) shouldBe false
        }
    }

    "a disabled button is disabled and does not call onClick" {
        var clicks = 0

        TestBed.preact(
            view = { KlangButton(onClick = { clicks++ }, availability = KlangButton.Availability.Disabled) { +"x" } },
        ) { root ->
            val button = root.selectCss<HTMLButtonElement>("button")

            button.single().disabled shouldBe true
            (styles.disabled.toString() in button.classes().single()) shouldBe true

            button.click()

            clicks shouldBe 0
        }
    }

    "a later button says it is unavailable, stays focusable, and does not call onClick" {
        var clicks = 0

        TestBed.preact(
            view = {
                KlangButton(onClick = { clicks++ }, availability = KlangButton.Availability.Later, beacon = true) {
                    +"Account"
                }
            },
        ) { root ->
            val button = root.selectCss<HTMLButtonElement>("button")

            button.single().disabled shouldBe false
            button.attr("aria-disabled") shouldContainExactly listOf("true")
            (styles.later.toString() in button.classes().single()) shouldBe true

            button.click()

            clicks shouldBe 0

            // The trail never points at a control that cannot be used
            (styles.beacon.toString() in button.classes().single()) shouldBe false
        }
    }

    "the beacon adds its class to an enabled button, never to a disabled one" {
        TestBed.preact(
            view = {
                KlangButton(onClick = {}, beacon = true) { +"Try me" }
                KlangButton(onClick = {}, beacon = true, availability = KlangButton.Availability.Disabled) { +"No" }
            },
        ) { root ->
            val classes = root.selectCss("button").classes()

            (styles.beacon.toString() in classes[0]) shouldBe true
            (styles.beacon.toString() in classes[1]) shouldBe false
        }
    }

    "a lamp sits before the content" {
        TestBed.preact(view = { KlangButton(onClick = {}, lamp = KlangLamp.State.On) { +"Songs" } }) { root ->
            val lamp = root.selectCss("button > span[data-lamp]")

            lamp.attr("data-lamp") shouldContainExactly listOf("On")
            root.selectCss("button").textContent() shouldBe "Songs"
            root.selectCss("button > :first-child").attr("data-lamp") shouldContainExactly listOf("On")
        }
    }
})
