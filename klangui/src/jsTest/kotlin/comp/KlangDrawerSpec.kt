/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.modals.modals
import io.peekandpoke.kraft.popups.popups
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.browser.document
import kotlinx.html.button
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The menu drawer (KLANG-00339): its destinations as tiles, the current one, those not there yet, close, slots. */
class KlangDrawerSpec : StringSpec({

    val entries = listOf(
        KlangDrawer.Entry(id = "songs", label = "Songs", sub = "Yours"),
        KlangDrawer.Entry(id = "learn", label = "Learn"),
        KlangDrawer.Entry(id = "account", label = "Account", sub = "Later", later = true),
    )

    fun tiles(root: Element): List<Element> {
        val list = root.querySelectorAll("[role=navigation] .${KlangControlStyles.tile}")

        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }

    "a navigation named by its title, a tile per destination, the current one marked, the others' lamps off" {
        TestBed.preact(view = { KlangDrawer(title = "Where to", entries = entries, current = "songs", onPick = {}) }) { root ->
            root.selectCss("[role=navigation]").attr("aria-label") shouldContainExactly listOf("Where to")

            val tiles = tiles(root.single())

            tiles.map { it.textContent } shouldContainExactly listOf("SongsYours", "Learn", "AccountLater")
            tiles.map { it.getAttribute("aria-current") } shouldContainExactly listOf("true", null, null)
            tiles.map { it.querySelector("[data-lamp]")?.getAttribute("data-lamp") } shouldContainExactly listOf("On", "Off", null)
            tiles.map { it.getAttribute("aria-disabled") } shouldContainExactly listOf(null, null, "true")
        }
    }

    "a press picks the destination; one that is not there yet picks nothing" {
        val picked = mutableListOf<String>()

        TestBed.preact(view = { KlangDrawer(title = "t", entries = entries, current = null, onPick = { picked.add(it) }) }) { root ->
            val tiles = tiles(root.single())

            (tiles[1] as HTMLElement).click()
            (tiles[2] as HTMLElement).click()

            picked shouldContainExactly listOf("learn")
        }
    }

    "the close button shows only with onClose, and closes" {
        var closed = 0

        TestBed.preact(
            view = {
                KlangDrawer(title = "a", entries = entries, current = null, onPick = {}, onClose = { closed++ })
                KlangDrawer(title = "b", entries = entries, current = null, onPick = {})
            },
        ) { root ->
            val closers = root.selectCss("button[aria-label=Close]")

            closers.size shouldBe 1

            (closers.single() as HTMLElement).click()

            closed shouldBe 1
        }
    }

    "the search and the footer go where they belong: the search above the tiles, the footer below" {
        TestBed.preact(
            view = {
                KlangDrawer(
                    title = "t",
                    entries = entries,
                    current = null,
                    onPick = {},
                    search = { span { +"SEARCH" } },
                    footer = { span { +"FOOTER" } },
                )
            },
        ) { root ->
            val text = root.selectCss("[role=navigation]").textContent()

            // The title "t", then the search, then the tiles
            text.startsWith("tSEARCH") shouldBe true
            (text.indexOf("FOOTER") > text.indexOf("AccountLater")) shouldBe true
            root.selectCss(".${KlangControlStyles.drawerFooter}").textContent() shouldBe "FOOTER"
        }
    }

    "without search and footer there is neither" {
        TestBed.preact(view = { KlangDrawer(title = "t", entries = entries, current = null, onPick = {}) }) { root ->
            root.selectCss(".${KlangControlStyles.drawerFooter}").toList().shouldBeEmpty()
        }
    }

    "a drawer that closes on Esc shows Close: Esc beside its close button, and Esc closes it through the key manager" {
        var closed = 0
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    KlangDrawer(title = "a", entries = entries, current = null, onPick = {}, onClose = { closed++ }, closeOnEsc = true)
                    KlangDrawer(title = "b", entries = entries, current = null, onPick = {}, onClose = {})
                },
            ) { root ->
                val pills = root.selectCss(".${KlangControlStyles.keyPill}")

                // One pill: only the drawer that closes on Esc shows it
                pills.size shouldBe 1
                pills.textContent().startsWith("Close:") shouldBe true

                document.body!!.dispatchEvent(
                    KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
                )

                closed shouldBe 1
            }
        } finally {
            manager.dispose()
        }
    }

    "a modal in front gets the Esc: the drawer behind it stays open, and closes on Esc once the modal is gone" {
        var closed = 0
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        fun esc() {
            document.body!!.dispatchEvent(
                KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
            )
        }

        try {
            TestBed.preact(
                appSetup = { keys(manager); modals() },
                view = {
                    FrontGrip()
                    KlangDrawer(title = "a", entries = entries, current = null, onPick = {}, onClose = { closed++ }, closeOnEsc = true)
                },
            ) { _ ->
                val modal = FrontGrip.modals.show<Unit> { span { +"MODAL" } }

                esc()

                closed shouldBe 0

                modal.close()
                esc()

                closed shouldBe 1
            }
        } finally {
            manager.dispose()
        }
    }

    "a popup blocks the drawer's Esc only while it holds the focus: a hover doc does not, a code tool's popover does" {
        var closed = 0
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        fun escOn(target: EventTarget) {
            target.dispatchEvent(
                KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
            )
        }

        try {
            TestBed.preact(
                appSetup = { keys(manager); popups() },
                view = {
                    FrontGrip()
                    KlangDrawer(title = "a", entries = entries, current = null, onPick = {}, onClose = { closed++ }, closeOnEsc = true)
                },
            ) { _ ->
                // A hover doc: open, but the focus is elsewhere
                FrontGrip.popups.add { span { +"HOVER" } }

                // Drawn, as a hover doc is while the mouse rests on a word
                eventually { document.body!!.textContent!!.contains("HOVER") shouldBe true }
                (document.activeElement as? HTMLElement)?.blur()
                escOn(document.body!!)

                closed shouldBe 1

                // A popover with the focus in it
                FrontGrip.popups.add { button { attributes["data-test"] = "in-popup" } }

                eventually { document.querySelector("[data-test=in-popup]") shouldNotBe null }

                val inPopup = document.querySelector("[data-test=in-popup]") as HTMLElement

                inPopup.focus()
                escOn(inPopup)

                closed shouldBe 1
            }
        } finally {
            manager.dispose()
        }
    }
})
