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

/** The close button: one look for every close, its name, its two sizes, a click; the drawer and the tool window use it. */
class KlangCloseButtonSpec : StringSpec({

    val styles = KlangControlStyles

    "a close button is named, round in both sizes, small only when asked, and reports a click" {
        var clicks = 0

        TestBed.preact(
            view = {
                KlangCloseButton(label = "Close", onClick = { clicks++ })
                KlangCloseButton(label = "Close Docs", onClick = {}, size = KlangCloseButton.Size.Small)
            },
        ) { root ->
            val buttons = root.selectCss("button")

            buttons.attr("aria-label") shouldContainExactly listOf("Close", "Close Docs")
            buttons.attr("title") shouldContainExactly listOf("Close", "Close Docs")
            buttons.classes().map { styles.round.toString() in it } shouldContainExactly listOf(true, true)
            buttons.classes().map { styles.roundSmall.toString() in it } shouldContainExactly listOf(false, true)

            (buttons[0] as HTMLElement).click()

            clicks shouldBe 1
        }
    }

    "the drawer and the tool window close with it, the window's at the small size" {
        TestBed.preact(
            view = {
                KlangDrawer(title = "Where to", entries = emptyList(), current = null, onPick = {}, onClose = {})
                KlangToolWindow(title = "Docs", pinned = true, onPinnedChange = {}, onClose = {}) {}
            },
        ) { root ->
            val drawerClose = root.selectCss("button[aria-label=Close]").classes().single()
            val windowClose = root.selectCss("button[aria-label='Close Docs']").classes().single()

            (styles.round.toString() in drawerClose) shouldBe true
            (styles.roundSmall.toString() in drawerClose) shouldBe false
            (styles.round.toString() in windowClose) shouldBe true
            (styles.roundSmall.toString() in windowClose) shouldBe true
        }
    }
})
