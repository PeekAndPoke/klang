/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.classes
import io.peekandpoke.kraft.testing.selectCss
import kotlinx.browser.document
import kotlinx.css.px
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The menu button (KLANG-00340): what it announces, its pressed and lit look, its ring, its size, a click, its key. */
class KlangMenuButtonSpec : StringSpec({

    val styles = KlangControlStyles

    "the menu button says whether its drawer is open, and is drawn pressed and lit, its ring lit, then; its ring's size is the caller's" {
        var clicks = 0

        TestBed.preact(
            view = {
                KlangMenuButton(open = false, onClick = { clicks++ }, size = 40.px)
                KlangMenuButton(open = true, onClick = {})
            },
        ) { root ->
            val buttons = root.selectCss("button")
            val frames = root.selectCss(".${styles.menuButtonFrame}")
            val rings = frames.map { it.querySelector("svg")!!.getAttribute("class")!! }

            buttons.attr("aria-label") shouldContainExactly listOf("Menu", "Menu")
            buttons.attr("aria-expanded") shouldContainExactly listOf("false", "true")
            (styles.pressedDark.toString() in buttons.classes()[0]) shouldBe false
            (styles.pressedDark.toString() in buttons.classes()[1]) shouldBe true
            (styles.menuButtonOpen.toString() in buttons.classes()[1]) shouldBe true
            (styles.menuButtonRingLit.toString() in rings[0]) shouldBe false
            (styles.menuButtonRingLit.toString() in rings[1]) shouldBe true
            (frames[0] as HTMLElement).style.width shouldBe "40px"
            buttons[0].querySelectorAll("circle").length shouldBe 9

            (buttons[0] as HTMLElement).click()

            clicks shouldBe 1
        }
    }

    "the trail's beacon shows on the closed button, not while its drawer is open (KLANG-00350)" {
        TestBed.preact(
            view = {
                KlangMenuButton(open = false, onClick = {}, beacon = true)
                KlangMenuButton(open = true, onClick = {}, beacon = true)
                KlangMenuButton(open = false, onClick = {})
            },
        ) { root ->
            val beacon = styles.beacon.toString()

            root.selectCss("button").classes().map { beacon in it } shouldContainExactly listOf(true, false, false)
        }
    }

    "with a key, the menu button answers it through the key manager (Alt+M in the app)" {
        var clicks = 0
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = { KlangMenuButton(open = false, onClick = { clicks++ }, key = KlangKeyBindings.MotorMenu) },
            ) { _ ->
                document.body!!.dispatchEvent(
                    KeyboardEvent(
                        "keydown",
                        KeyboardEventInit(key = "m", code = "KeyM", altKey = true, bubbles = true, cancelable = true),
                    )
                )

                clicks shouldBe 1
            }
        } finally {
            manager.dispose()
        }
    }
})
