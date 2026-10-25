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
import io.peekandpoke.kraft.testing.selectCss
import kotlinx.css.px
import org.w3c.dom.HTMLElement

/** The menu button (KLANG-00340): what it announces, its pressed and lit look, its size, a click. */
class KlangMenuButtonSpec : StringSpec({

    val styles = KlangControlStyles

    "the menu button says whether its drawer is open, and is drawn pressed and lit then; its size is the caller's" {
        var clicks = 0

        TestBed.preact(
            view = {
                KlangMenuButton(open = false, onClick = { clicks++ }, size = 40.px)
                KlangMenuButton(open = true, onClick = {})
            },
        ) { root ->
            val buttons = root.selectCss("button")

            buttons.attr("aria-label") shouldContainExactly listOf("Menu", "Menu")
            buttons.attr("aria-expanded") shouldContainExactly listOf("false", "true")
            (styles.pressedDark.toString() in buttons.classes()[0]) shouldBe false
            (styles.pressedDark.toString() in buttons.classes()[1]) shouldBe true
            (styles.menuButtonOpen.toString() in buttons.classes()[1]) shouldBe true
            (buttons[0] as HTMLElement).style.width shouldBe "40px"
            buttons[0].querySelectorAll("circle").length shouldBe 9

            (buttons[0] as HTMLElement).click()

            clicks shouldBe 1
        }
    }
})
