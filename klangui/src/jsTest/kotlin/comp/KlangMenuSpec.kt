/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** A menu whose items its parent changes, as an in-place menu's items may change while it is shown. */
private class MenuHost(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: MenuHost? = null
    }

    var lastEnabled: Boolean by value(true)

    init {
        current = this
    }

    override fun VDom.render() {
        KlangMenu(
            label = "m",
            items = listOf(
                KlangMenu.Item(label = "One", onSelect = {}),
                KlangMenu.Item(label = "Two", onSelect = {}),
                KlangMenu.Item(label = "Last", enabled = lastEnabled, onSelect = {}),
            ),
        )
    }
}

@Suppress("FunctionName")
private fun Tag.MenuHost() = comp { MenuHost(it) }

/** The menu (KLANG-00339): a menu of menuitems, the keys a menu takes, choosing closes. */
class KlangMenuSpec : StringSpec({

    val log = mutableListOf<String>()

    fun items() = listOf(
        KlangMenu.Item(label = "One", hint = "Alt 1", onSelect = { log.add("one") }),
        KlangMenu.Item(label = "Two", onSelect = { log.add("two") }),
        KlangMenu.Item(label = "Off", enabled = false, onSelect = { log.add("off") }),
        KlangMenu.Item(label = "Three", onSelect = { log.add("three") }),
    )

    fun press(key: String) {
        val target = document.activeElement ?: document.body!!

        target.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = key, bubbles = true, cancelable = true)))
    }

    /** The label of the focused menu item; null when the focus is on no menu item. */
    fun focused(): String? = (document.activeElement as? Element)
        ?.takeIf { it.getAttribute("role") == "menuitem" }
        ?.querySelector("span")
        ?.textContent

    beforeTest { log.clear() }

    "a menu named by its label, its items menuitems with one tab stop, a hint printed beside" {
        TestBed.preact(view = { KlangMenu(label = "Code actions", items = items(), focusOnOpen = false) }) { root ->
            root.selectCss("[role=menu]").attr("aria-label") shouldContainExactly listOf("Code actions")

            val menuItems = root.selectCss("[role=menuitem]")

            // One tab stop: the first item that can be chosen
            menuItems.attr("tabindex") shouldContainExactly listOf("0", "-1", "-1", "-1")
            menuItems.map { it.textContent } shouldContainExactly listOf("OneAlt 1", "Two", "Off", "Three")
            menuItems.map { (it as HTMLElement).asDynamic().disabled as Boolean } shouldContainExactly listOf(false, false, true, false)
        }
    }

    "opened, it focuses its first item; Down and Up move round the ends, past the one that cannot be chosen" {
        TestBed.preact(view = { KlangMenu(label = "m", items = items()) }) { _ ->
            eventually { focused() shouldBe "One" }

            press("ArrowDown")
            focused() shouldBe "Two"

            press("ArrowDown")
            focused() shouldBe "Three"

            press("ArrowDown")
            focused() shouldBe "One"

            press("ArrowUp")
            focused() shouldBe "Three"

            press("Home")
            focused() shouldBe "One"

            press("End")
            focused() shouldBe "Three"
        }
    }

    "Esc closes; choosing an item runs it and closes" {
        var closed = 0

        TestBed.preact(view = { KlangMenu(label = "m", items = items(), onClose = { closed++ }) }) { root ->
            eventually { focused() shouldBe "One" }

            press("Escape")

            closed shouldBe 1

            (root.selectCss("[role=menuitem]")[1] as HTMLElement).click()

            log shouldContainExactly listOf("two")
            closed shouldBe 2
        }
    }

    "the tab stop follows the focus, so Tab comes back to the item last used; it skips an item that cannot be chosen" {
        TestBed.preact(view = { KlangMenu(label = "m", items = items()) }) { root ->
            eventually { focused() shouldBe "One" }

            press("End")

            eventually {
                root.selectCss("[role=menuitem]").attr("tabindex") shouldContainExactly listOf("-1", "-1", "-1", "0")
            }
        }

        val offFirst = listOf(
            KlangMenu.Item(label = "Off", enabled = false, onSelect = {}),
            KlangMenu.Item(label = "On", onSelect = {}),
        )

        TestBed.preact(view = { KlangMenu(label = "m", items = offFirst, focusOnOpen = false) }) { root ->
            root.selectCss("[role=menuitem]").attr("tabindex") shouldContainExactly listOf("-1", "0")
        }
    }

    "when the item holding the tab stop can no longer be chosen, the stop goes back to the first that can" {
        TestBed.preact(view = { MenuHost() }) { root ->
            eventually { focused() shouldBe "One" }

            press("End")

            eventually {
                root.selectCss("[role=menuitem]").attr("tabindex") shouldContainExactly listOf("-1", "-1", "0")
            }

            MenuHost.current!!.lastEnabled = false

            eventually {
                root.selectCss("[role=menuitem]").attr("tabindex") shouldContainExactly listOf("0", "-1", "-1")
            }
        }
    }

    "Tab closes the menu and lets the focus move on" {
        var closed = 0

        TestBed.preact(view = { KlangMenu(label = "m", items = items(), onClose = { closed++ }) }) { _ ->
            eventually { focused() shouldBe "One" }

            val target = document.activeElement!!
            val tab = KeyboardEvent("keydown", KeyboardEventInit(key = "Tab", bubbles = true, cancelable = true))

            target.dispatchEvent(tab)

            closed shouldBe 1
            // Not taken: the browser moves the focus on
            tab.defaultPrevented shouldBe false
        }
    }

    "shown in place, it leaves the focus where it is" {
        TestBed.preact(view = { KlangMenu(label = "m", items = items(), focusOnOpen = false) }) { _ ->
            focused() shouldBe null
        }
    }
})
