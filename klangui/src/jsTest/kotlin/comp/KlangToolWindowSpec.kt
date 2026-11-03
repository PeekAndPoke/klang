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
import io.peekandpoke.kraft.testing.textContent
import kotlinx.html.span
import org.w3c.dom.HTMLElement

/** The tool window (KLANG-00339): flying in or pinned, its pin and close, its title, chip and content. */
class KlangToolWindowSpec : StringSpec({

    val styles = KlangControlStyles

    "unpinned it flies in, pinned it does not; named by its title, with its chip and content" {
        TestBed.preact(
            view = {
                KlangToolWindow(title = "Docs", pinned = false, chip = "follows the cursor") { span { +"BODY" } }
                KlangToolWindow(title = "Keys", pinned = true) { span { +"BODY" } }
            },
        ) { root ->
            val windows = root.selectCss("section")

            windows.attr("aria-label") shouldContainExactly listOf("Docs", "Keys")
            (styles.toolWindowFloating.toString() in windows.classes()[0]) shouldBe true
            (styles.toolWindowFloating.toString() in windows.classes()[1]) shouldBe false

            root.selectCss(".${styles.toolWindowChip}").textContent() shouldBe "follows the cursor"
            root.selectCss(".${styles.toolWindowBody}").map { it.textContent } shouldContainExactly listOf("BODY", "BODY")
        }
    }

    "the pin says its state and asks for the other one; close closes; without them there are no buttons" {
        val pins = mutableListOf<Boolean>()
        var closed = 0

        TestBed.preact(
            view = {
                KlangToolWindow(title = "Docs", pinned = false, onPinnedChange = { pins.add(it) }, onClose = { closed++ }) {}
                KlangToolWindow(title = "Keys", pinned = true, onPinnedChange = { pins.add(it) }) {}
                KlangToolWindow(title = "Bare", pinned = false) {}
            },
        ) { root ->
            val windows = root.selectCss("section")
            val pinsOf = windows.map { it.querySelector("button[aria-pressed]") as HTMLElement? }

            pinsOf.map { it?.getAttribute("aria-pressed") } shouldContainExactly listOf("false", "true", null)

            pinsOf[0]!!.click()
            pinsOf[1]!!.click()

            pins shouldContainExactly listOf(true, false)

            val close = windows[0].querySelector("button[aria-label='Close Docs']") as HTMLElement

            close.click()

            closed shouldBe 1
            windows[2].querySelectorAll("button").length shouldBe 0
        }
    }

    "a tab that counts shows a badge after its title and says it in its name; 99 as it is, past 99 as 99+; at 0 none" {
        TestBed.preact(
            view = {
                KlangToolWindow(
                    title = "Tools",
                    pinned = true,
                    tabs = listOf(
                        KlangToolWindow.Tab(id = "midi", title = "MIDI"),
                        KlangToolWindow.Tab(id = "console", title = "Console", count = 3),
                        KlangToolWindow.Tab(id = "full", title = "Full", count = 99),
                        KlangToolWindow.Tab(id = "many", title = "Many", count = 120),
                    ),
                ) {}
            },
        ) { root ->
            val tabs = root.selectCss(".${styles.toolWindowTab}")

            tabs.map { it.querySelector(".${styles.toolWindowTabCount}")?.textContent } shouldContainExactly
                listOf(null, "3", "99", "99+")
            tabs.map { it.getAttribute("aria-label") } shouldContainExactly
                listOf(null, "Console (3 new)", "Full (99 new)", "Many (99+ new)")
        }
    }
})
