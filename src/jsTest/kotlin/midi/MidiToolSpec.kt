/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.comp.KlangWorkbench

/** The MIDI tool on every page (KLANG-00402, step 4): a tab of the page's bottom panel, or a panel of its own. */
class MidiToolSpec : StringSpec({

    "a page with no bottom panel gets the MIDI tool alone; a page's own panel gets it after its own tabs" {
        withClue("no panel of the page's own: the MIDI tool's") {
            MidiTool.with(page = null).tabs.map { it.id } shouldBe listOf(MidiTool.TAB_ID)
        }

        val help = KlangWorkbench.Tab(id = "help", title = "Help") {}
        val page = KlangWorkbench.Bottom(title = "Help", icon = { question_circle }, tabs = listOf(help))
        val withTool = MidiTool.with(page = page)

        withClue("the page's panel, its title and its tabs kept, the MIDI tool last") {
            withTool.title shouldBe "Help"
            withTool.tabs.map { it.id } shouldBe listOf("help", MidiTool.TAB_ID)
        }
    }
})
