/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/** A workbench with the help panel at its bottom, tracked as the app tracks its own (a tracker takes hold in a host). */
private class PanelHost(ctx: Ctx<QuickHelpPanel>) : Component<QuickHelpPanel>(ctx) {
    override fun VDom.render() {
        KlangWorkbench(
            column = { span { +"COLUMN" } },
            motor = { _ -> span { +"MOTOR" } },
            content = { span { +"CONTENT" } },
            bottom = props.bottom(),
        ).track(WorkbenchSlots.workbench)
    }
}

@Suppress("FunctionName")
private fun Tag.PanelHost(panel: QuickHelpPanel) = comp(panel) { PanelHost(it) }

/** The help panel in the workbench's bottom slot (KLANG-00349, KLANG-00365): which tab a name's help turns it to. */
class QuickHelpPanelSpec : StringSpec({

    val note = KlangSymbol(
        name = "note",
        category = "tonal",
        origin = KlangSymbol.Origin.Library("sprudel"),
        variants = emptyList(),
    )

    /** The bottom panel's tab keys by their title, and which one is shown. */
    fun Element.shownTab(): String? {
        val keys = querySelectorAll("button[aria-pressed]")

        return (0 until keys.length).map { keys.item(it) as HTMLElement }
            .firstOrNull { it.getAttribute("aria-pressed") == "true" && it.textContent in setOf("Quick help", "Lexikon", "Sprudel") }
            ?.textContent
    }

    /** The bottom panel's tab keys, by their title: none while the panel is closed. */
    fun Element.tabKeys(): List<String?> {
        val keys = querySelectorAll("button[aria-pressed]")

        return (0 until keys.length).map { (keys.item(it) as HTMLElement).textContent }
            .filter { it in setOf("Quick help", "Lexikon", "Sprudel") }
    }

    "the Lexikon shown: a name's help, by a hover too, turns the panel to the Quick help" {
        val panel = QuickHelpPanel(pageKey = "spec")
        // The app's key manager: an open panel's close key answers through it, and without one it does not draw
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(
                appSetup = {
                    addons { marked() }
                    keys(manager)
                },
                view = { PanelHost(panel) },
            ) { root ->
                val view = root.single()

                // Asked again until the workbench is tracked: it is, once drawn
                eventually {
                    panel.showLexikon(slug = "adsr")
                    view.shownTab() shouldBe "Lexikon"
                }

                // As a hover asks for it, with "Show on hover" on: the maintainer, 2026-10-27
                panel.showHelp(entry = HelpWindow.Entry(symbol = note, declarationCode = null), jumpTo = {})

                eventually { view.shownTab() shouldBe "Quick help" }
            }
        } finally {
            manager.dispose()
        }
    }

    "a right-click's \"Open the Sprudel tool\" turns the panel to the Sprudel tab; a page without tools has none to turn to" {
        val withTools = QuickHelpPanel(pageKey = "spec-tools", toolContext = { null })
        val withoutTools = QuickHelpPanel(pageKey = "spec-plain")
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { addons { marked() }; keys(manager) }, view = { PanelHost(withoutTools) }) { root ->
                val view = root.single()

                // The panel closed: asked for a tab it does not have, it stays closed
                eventually { (view.textContent?.contains("CONTENT") == true) shouldBe true }
                delay(200)
                withoutTools.showSprudel()
                delay(200)
                view.tabKeys() shouldBe emptyList()

                eventually {
                    withoutTools.showLexikon(slug = "adsr")
                    view.shownTab() shouldBe "Lexikon"
                }
            }

            TestBed.preact(appSetup = { addons { marked() }; keys(manager) }, view = { PanelHost(withTools) }) { root ->
                val view = root.single()

                eventually {
                    withTools.showSprudel()
                    view.shownTab() shouldBe "Sprudel"
                }
            }
        } finally {
            manager.dispose()
        }
    }
})
