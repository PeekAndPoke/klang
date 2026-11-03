/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.comp.ConsoleTool
import io.peekandpoke.klang.midi.MidiTool
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.visuals.VisualsTab

/**
 * The bottom panel, the same on every page (KLANG-00409): a page's own tabs, then the app's (the Visuals, the MIDI tool
 * of KLANG-00402, the Console of KLANG-00407), its key counting the Console's unseen errors.
 */
class BottomToolsSpec : StringSpec({

    val appTabs = listOf(VisualsTab.TAB_ID, MidiTool.TAB_ID, ConsoleTool.TAB_ID)

    "one panel on every page: the page's tabs first, then the app's; one title, one icon, the walkthroughs' steps on its key and the Visuals" {
        val help = KlangWorkbench.Tab(id = "help", title = "Help") {}

        listOf(emptyList(), listOf(help)).forEach { pageTabs ->
            withClue("the page's tabs: ${pageTabs.map { it.id }}") {
                val panel = BottomTools.with(pageTabs = pageTabs, unseenErrors = 0)

                panel.tabs.map { it.id } shouldBe pageTabs.map { it.id } + appTabs
                panel.title shouldBe BottomTools.TITLE
                panel.icon shouldBeSameInstanceAs BottomTools.ICON
                panel.beaconStep shouldBe KlangGuiDance.BOTTOM_PANEL
                panel.tabs.first { it.id == VisualsTab.TAB_ID }.beaconStep shouldBe KlangGuiDance.VISUALS_TAB
            }
        }
    }

    "the unseen errors on the panel's key and in the Console's tab" {
        val counted = BottomTools.with(pageTabs = emptyList(), unseenErrors = 3)

        counted.count shouldBe 3
        counted.countTab shouldBe ConsoleTool.TAB_ID
        counted.tabs.first { it.id == ConsoleTool.TAB_ID }.count shouldBe 3

        val none = BottomTools.with(pageTabs = emptyList(), unseenErrors = 0)

        none.count shouldBe 0
        none.tabs.first { it.id == ConsoleTool.TAB_ID }.count shouldBe 0
    }
})
