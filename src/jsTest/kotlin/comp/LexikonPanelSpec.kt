/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.lexikon.allLexikonEntries
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/** The Lexikon tab (KLANG-00365, KLANG-00369): an entry's terms link to their entries, and a link followed stays here. */
class LexikonPanelSpec : StringSpec({

    "an entry links the terms its detail names, never itself; a plain click shows the other entry here" {
        val slug = StreamSource<String?>("voice")
        val picked = mutableListOf<String?>()

        TestBed.preact(
            appSetup = { addons { marked() } },
            view = { LexikonPanel(slug = slug, onPick = { picked.add(it) }) },
        ) { root ->
            val panel = root.single()

            eventually { (panel.querySelector("a[href='/manuals/lexikon/pitch']") != null) shouldBe true }

            panel.querySelector("a[href='/manuals/lexikon/voice']") shouldBe null

            // Stopped here as well, so the test page never leaves; the panel's own stop is what is asked
            val stoppedByPanel = mutableListOf<Boolean>()
            val stopper: (Event) -> Unit = { event ->
                stoppedByPanel.add(event.defaultPrevented)
                event.preventDefault()
            }

            window.addEventListener("click", stopper)

            try {
                panel.querySelector("a[href='/manuals/lexikon/pitch']")!!.dispatchEvent(
                    MouseEvent("click", MouseEventInit(bubbles = true, cancelable = true)),
                )
            } finally {
                window.removeEventListener("click", stopper)
            }

            picked shouldContainExactly listOf("pitch")
            stoppedByPanel shouldContainExactly listOf(true)
        }
    }

    "the terms on the left: by name, the one shown marked; a press picks one; the search narrows them" {
        val slug = StreamSource<String?>("voice")
        val picked = mutableListOf<String?>()

        TestBed.preact(
            appSetup = { addons { marked() } },
            view = { LexikonPanel(slug = slug, onPick = { picked.add(it) }) },
        ) { root ->
            val panel = root.single()

            fun terms(): List<String> {
                val items = panel.querySelectorAll("[role=list] [role=listitem] button[data-term]")

                return (0 until items.length).map { (items.item(it) as Element).getAttribute("data-term")!! }
            }

            eventually { terms().size shouldBe allLexikonEntries.size }

            terms() shouldBe allLexikonEntries.sortedBy { it.term.lowercase() }.map { it.slug }
            panel.querySelector("[aria-current=true]")?.getAttribute("data-term") shouldBe "voice"

            (panel.querySelector("button[data-term='pitch']") as HTMLElement).click()
            picked shouldContainExactly listOf("pitch")

            val input = panel.querySelector("input") as HTMLInputElement

            input.value = "perlin"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually { terms() shouldBe listOf("perlin-noise") }

            input.value = "no such term at all"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually {
                terms() shouldBe emptyList()
                panel.textContent!!.contains("No term matches.") shouldBe true
            }
        }
    }
})
