/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.attr
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

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
})
