/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.comp.ConsoleTool
import io.peekandpoke.klang.midi.MidiTool
import io.peekandpoke.klang.ui.comp.KlangWorkbench

/**
 * The tools on every page (KLANG-00402, step 4; KLANG-00407): the MIDI tool and the Console, tabs of the page's bottom
 * panel or a panel of their own, its key counting the Console's unseen errors.
 */
class BottomToolsSpec : StringSpec({

    val tools = listOf(MidiTool.TAB_ID, ConsoleTool.TAB_ID)

    "a page with no bottom panel gets the tools alone, closed until opened; a page's own panel gets them after its own tabs" {
        val alone = BottomTools.with(page = null, unseenErrors = 0)

        withClue("no panel of the page's own: the tools'") {
            alone.title shouldBe BottomTools.TITLE
            alone.tabs.map { it.id } shouldBe tools
            alone.ambient shouldBe true
        }

        val help = KlangWorkbench.Tab(id = "help", title = "Help") {}
        val page = KlangWorkbench.Bottom(title = "Help", icon = { question_circle }, tabs = listOf(help))
        val withTools = BottomTools.with(page = page, unseenErrors = 0)

        withClue("the page's panel, its title and its tabs kept, the tools last") {
            withTools.title shouldBe "Help"
            withTools.ambient shouldBe false
            withTools.tabs.map { it.id } shouldBe listOf("help") + tools
        }
    }

    "the unseen errors on the panel's key and in the Console's tab, on either panel" {
        val help = KlangWorkbench.Tab(id = "help", title = "Help") {}
        val page = KlangWorkbench.Bottom(title = "Help", icon = { question_circle }, tabs = listOf(help))

        listOf(null, page).forEach { own ->
            withClue("page panel: ${own != null}") {
                val counted = BottomTools.with(page = own, unseenErrors = 3)

                counted.count shouldBe 3
                counted.countTab shouldBe ConsoleTool.TAB_ID
                counted.tabs.first { it.id == ConsoleTool.TAB_ID }.title shouldBe "Console (3)"

                val none = BottomTools.with(page = own, unseenErrors = 0)

                none.count shouldBe 0
                none.tabs.first { it.id == ConsoleTool.TAB_ID }.title shouldBe "Console"
            }
        }
    }
})
