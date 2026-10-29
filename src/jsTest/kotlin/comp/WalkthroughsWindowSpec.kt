/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.ui.GuiDance
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.semanticui.semanticUI
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** The page under test: it opens the window over a dance of its own and records the mutes asked for. */
private class WalkthroughsPage(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: WalkthroughsPage? = null
    }

    var dance = GuiDance(arcs = emptyList(), store = GuiDance.MemoryStore())
    val calls = mutableListOf<String>()

    init {
        current = this
    }

    fun open(dance: GuiDance) {
        this.dance = dance

        modals.show<Boolean> { handle ->
            WalkthroughsWindow(
                handle = handle,
                dance = dance,
                mute = { calls.add("mute") },
                unmute = { calls.add("unmute") },
            )
        }
    }

    override fun VDom.render() {
        div { +"PAGE" }
    }
}

@Suppress("FunctionName")
private fun Tag.WalkthroughsPage() = comp { WalkthroughsPage(it) }

private val arcs = listOf(
    GuiDance.Arc(id = "first", title = "Your first song", steps = listOf("song.play")),
    GuiDance.Arc(id = "second", title = "Fullscreen", steps = listOf("workbench.fullscreen")),
)

/** The walkthroughs window (KLANG-00350): marks and choices apply on Confirm only; Cancel leaves all as it was. */
class WalkthroughsWindowSpec : StringSpec({

    suspend fun withPage(block: suspend (WalkthroughsPage) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { semanticUI(); keys(manager) }, view = { WalkthroughsPage() }) {
                block(WalkthroughsPage.current!!)
            }
        } finally {
            manager.dispose()
        }
    }

    fun dialog(): Element? = document.querySelector("[role=dialog]")

    fun key(text: String): HTMLElement {
        val buttons = dialog()!!.querySelectorAll("button")

        return (0 until buttons.length).map { buttons.item(it) as HTMLElement }.first { it.textContent?.contains(text) == true }
    }

    /** The card of the walkthrough titled [title]. */
    fun card(title: String): HTMLElement {
        val cards = dialog()!!.querySelectorAll("[data-arc]")

        return (0 until cards.length).map { cards.item(it) as HTMLElement }.first { it.textContent?.contains(title) == true }
    }

    /** The link [label] on the card of the walkthrough titled [title]. */
    fun link(title: String, label: String): HTMLElement {
        val links = card(title).querySelectorAll("button")

        return (0 until links.length).map { links.item(it) as HTMLElement }.first { it.textContent == label }
    }

    "a finished walkthrough marked, then Cancel: nothing changes" {
        withPage { page ->
            page.open(GuiDance(arcs = arcs, store = GuiDance.MemoryStore(done = setOf("first"))))

            eventually { dialog()?.textContent shouldContain "1 more to come." }

            link("Your first song", "Reopen").click()

            // Marked: it stays on the list, its lamp off, its link now taking the mark back, and the count counts it
            eventually {
                dialog()!!.textContent shouldContain "2 more to come."
                card("Your first song").querySelector("[data-lamp]")?.getAttribute("data-lamp") shouldBe "Off"
                link("Your first song", "Complete")
            }

            key("Cancel").click()

            eventually { dialog() shouldBe null }
            page.dance.doneArcs().map { it.id } shouldContainExactly listOf("first")
        }
    }

    "a finished walkthrough marked, then Confirm: it is taken again" {
        withPage { page ->
            page.open(GuiDance(arcs = arcs, store = GuiDance.MemoryStore(done = setOf("first"))))

            eventually { (dialog() != null) shouldBe true }

            link("Your first song", "Reopen").click()
            eventually { dialog()!!.textContent shouldContain "2 more to come." }
            key("Confirm").click()

            eventually { dialog() shouldBe null }
            page.dance.doneArcs() shouldBe emptyList()
            page.dance.lit() shouldBe "song.play"
            page.calls shouldBe emptyList()
        }
    }

    "a mute chosen, then disabled again as the mark is undone, is not applied" {
        withPage { page ->
            page.open(GuiDance(arcs = arcs, store = GuiDance.MemoryStore(done = setOf("first", "second"))))

            eventually { dialog()?.textContent shouldContain "That was every walkthrough" }

            link("Your first song", "Reopen").click()
            eventually { dialog()!!.textContent shouldContain "1 more to come." }
            key("Mute for an hour").click()
            eventually { link("Your first song", "Complete").click() }
            eventually { dialog()!!.textContent shouldContain "That was every walkthrough" }
            key("Confirm").click()

            eventually { dialog() shouldBe null }
            page.calls shouldBe emptyList()
        }
    }

    "taken again while muted, the mute left as it was: Confirm unmutes too" {
        withPage { page ->
            val muted = GuiDance.MemoryStore(done = setOf("first"), mutedUntil = 9.0e15)

            page.open(GuiDance(arcs = arcs, store = muted))

            eventually { dialog()?.textContent shouldContain "Muted until" }

            link("Your first song", "Reopen").click()
            eventually { dialog()!!.textContent shouldContain "2 more to come." }
            key("Confirm").click()

            eventually { dialog() shouldBe null }
            page.calls shouldContainExactly listOf("unmute")
        }
    }

    "a walkthrough to come, completed, then Confirm: it is finished without dancing it" {
        withPage { page ->
            page.open(GuiDance(arcs = arcs, store = GuiDance.MemoryStore()))

            eventually { dialog()?.textContent shouldContain "2 more to come." }

            link("Your first song", "Complete").click()
            eventually { dialog()!!.textContent shouldContain "1 more to come." }
            key("Confirm").click()

            eventually { dialog() shouldBe null }
            page.dance.doneArcs().map { it.id } shouldContainExactly listOf("first")
            page.dance.lit() shouldBe "workbench.fullscreen"
        }
    }

    "Start begins a walkthrough at once, out of its order, the window closed and a mute lifted" {
        withPage { page ->
            page.open(GuiDance(arcs = arcs, store = GuiDance.MemoryStore(mutedUntil = 9.0e15)))

            eventually { dialog()?.textContent shouldContain "Muted until" }

            link("Fullscreen", "Start").click()

            eventually { dialog() shouldBe null }
            page.dance.currentArc()?.id shouldBe "second"
            page.calls shouldContainExactly listOf("unmute")
        }
    }
})
