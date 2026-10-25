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
import org.w3c.dom.HTMLElement

/** The icon toggle (KLANG-00340): its name, whether it is on, its look, a click. */
class KlangIconToggleSpec : StringSpec({

    val styles = KlangControlStyles

    "an icon toggle is named, says whether it is on, is drawn sunk in then, and reports a click" {
        var clicks = 0

        TestBed.preact(
            view = {
                KlangIconToggle(icon = { book }, label = "Docs", on = false, onClick = { clicks++ })
                KlangIconToggle(icon = { book }, label = "Keys", on = true, large = true, onClick = {})
            },
        ) { root ->
            val buttons = root.selectCss("button")

            buttons.attr("aria-label") shouldContainExactly listOf("Docs", "Keys")
            buttons.attr("aria-pressed") shouldContainExactly listOf("false", "true")
            (styles.iconToggleOn.toString() in buttons.classes()[0]) shouldBe false
            (styles.iconToggleOn.toString() in buttons.classes()[1]) shouldBe true
            (styles.iconToggleLarge.toString() in buttons.classes()[1]) shouldBe true

            (buttons[0] as HTMLElement).click()

            clicks shouldBe 1
        }
    }
})
