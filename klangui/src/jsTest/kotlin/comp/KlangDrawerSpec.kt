/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.browser.document
import kotlinx.html.button
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The Motor's menu (KLANG-00339, KLANG-00403): its destinations as tiles, the current one, those not there yet, close, slots. */
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

    "a navigation named by its title, a tile per destination, the current one marked, the others' lamps off, a later one unlit" {
        TestBed.preact(view = { KlangDrawer(title = "Where to", entries = entries, current = "songs", onPick = {}) }) { root ->
            root.selectCss("[role=navigation]").attr("aria-label") shouldContainExactly listOf("Where to")

            val tiles = tiles(root.single())

            tiles.map { it.textContent } shouldContainExactly listOf("SongsYours", "Learn", "AccountLater")
            tiles.map { it.getAttribute("aria-current") } shouldContainExactly listOf("true", null, null)
            tiles.map { it.querySelector("[data-lamp]")?.getAttribute("data-lamp") } shouldContainExactly listOf("On", "Off", "Unlit")
            tiles.map { it.getAttribute("aria-disabled") } shouldContainExactly listOf(null, null, "true")
        }
    }

    "the destination the trail lights carries the beacon, and only it (KLANG-00350)" {
        TestBed.preact(view = { KlangDrawer(title = "t", entries = entries, current = null, onPick = {}, beacon = "learn") }) { root ->
            val beacon = KlangControlStyles.beacon.toString()

            tiles(root.single()).map { beacon in (it.getAttribute("class") ?: "") } shouldContainExactly listOf(false, true, false)
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

    "the search leads the header, the close button after it, the title not printed; the halves under the tiles" {
        TestBed.preact(
            view = {
                KlangDrawer(
                    title = "t",
                    entries = entries,
                    current = null,
                    onPick = {},
                    onClose = {},
                    search = { span { +"SEARCH" } },
                    pickUp = { span { +"PICKUP" } },
                    loadouts = { span { +"LOADOUTS" } },
                )
            },
        ) { root ->
            val header = root.selectCss(".${KlangControlStyles.drawerHeader}").single()

            // The search, then the close button: one row; the title names the menu without being printed
            header.textContent!!.startsWith("SEARCH") shouldBe true
            header.querySelector(".${KlangControlStyles.drawerSearch}")!!.textContent shouldBe "SEARCH"
            header.querySelector("button[aria-label=Close]") shouldNotBe null

            val text = root.selectCss("[role=navigation]").textContent()

            // Under the tiles, the halves in their order: what one comes back to, then the loadouts
            (text.indexOf("PICKUP") > text.indexOf("AccountLater")) shouldBe true
            (text.indexOf("LOADOUTS") > text.indexOf("PICKUP")) shouldBe true

            val below = root.selectCss(".${KlangControlStyles.drawerBelow}").single()

            below.classList.contains(KlangControlStyles.drawerBelowSplit.toString()) shouldBe true
            below.querySelectorAll(".${KlangControlStyles.drawerSection}").length shouldBe 2
        }
    }

    "one half alone takes the whole width; with neither there is nothing under the tiles" {
        TestBed.preact(
            view = { KlangDrawer(title = "t", entries = entries, current = null, onPick = {}, loadouts = { span { +"LOADOUTS" } }) },
        ) { root ->
            val below = root.selectCss(".${KlangControlStyles.drawerBelow}").single()

            below.classList.contains(KlangControlStyles.drawerBelowSplit.toString()) shouldBe false
            below.textContent shouldBe "LOADOUTS"
        }

        TestBed.preact(view = { KlangDrawer(title = "t", entries = entries, current = null, onPick = {}) }) { root ->
            root.selectCss(".${KlangControlStyles.drawerBelow}").toList().shouldBeEmpty()
            root.selectCss(".${KlangControlStyles.drawerSearch}").toList().shouldBeEmpty()
        }
    }

    "the lit destination has its lamp on also where it is not the current one, which alone is pressed" {
        TestBed.preact(view = { KlangDrawer(title = "t", entries = entries, current = null, lit = "learn", onPick = {}) }) { root ->
            val tiles = tiles(root.single())

            withClue("the section one is in lit, nothing pressed") {
                tiles.map { it.querySelector("[data-lamp]")?.getAttribute("data-lamp") } shouldContainExactly
                    listOf("Off", "On", "Unlit")
                tiles.map { it.getAttribute("aria-current") } shouldContainExactly listOf(null, null, null)
            }
        }
    }

    "a destination's icon stands at the tile's right end, after its label, hidden from screen readers; one without has none" {
        val withIcon = listOf(
            KlangDrawer.Entry(id = "songs", label = "Songs", icon = { music }),
            KlangDrawer.Entry(id = "learn", label = "Learn"),
        )

        TestBed.preact(view = { KlangDrawer(title = "t", entries = withIcon, current = null, onPick = {}) }) { root ->
            val tiles = tiles(root.single())
            val icon = tiles[0].querySelector(".${KlangControlStyles.drawerTile} > i")!!

            icon.classList.contains("music") shouldBe true
            icon.getAttribute("aria-hidden") shouldBe "true"
            withClue("the lamp before the label, left of the headline") {
                val head = tiles[0].querySelector(".${KlangControlStyles.drawerTileHead}")!!

                head.firstElementChild!!.hasAttribute("data-lamp") shouldBe true
            }
            withClue("after the text, at the right end") {
                (icon.parentElement!!.lastElementChild === icon) shouldBe true
                icon.previousElementSibling!!.classList.contains(KlangControlStyles.drawerTileText.toString()) shouldBe true
            }
            // Beside the label, not in it: the tile's name stays the label and its line
            tiles[0].querySelector(".${KlangControlStyles.drawerLabel}")!!.textContent shouldBe "Songs"
            tiles[1].querySelector("i") shouldBe null
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
})
