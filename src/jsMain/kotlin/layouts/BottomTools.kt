/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.comp.ConsoleTool
import io.peekandpoke.klang.midi.MidiTool
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.visuals.VisualsTab
import io.peekandpoke.ultra.semanticui.SemanticIconFn

/**
 * The bottom panel, the same on every page (KLANG-00409): its title, its icon, the walkthrough's step on its key, a
 * page's own tabs and after them the app's, there on every page: the Visuals, the MIDI tool (KLANG-00402, step 4) and
 * the Console (KLANG-00407).
 */
object BottomTools {

    /** The panel's title on every page (KLANG-00409, D2). */
    const val TITLE = "Tools"

    /**
     * The panel's key wears the same icon on every page (the maintainer, 2026-11-03: "this icon here should always be
     * the same"), the tools the song page's panel had ("please replace it with the other 'tools' icon that we
     * already had on the song page").
     */
    val ICON: SemanticIconFn = { tools }

    /**
     * The panel with the [pageTabs] first, then the app's tabs. Its key and the Console's tab count the [unseenErrors];
     * opened by its key while they count, it opens at the Console.
     */
    fun with(pageTabs: List<KlangWorkbench.Tab>, unseenErrors: Int): KlangWorkbench.Bottom = KlangWorkbench.Bottom(
        title = TITLE,
        icon = ICON,
        // Its key, the Visuals walkthrough's first step, on every page
        beaconStep = KlangGuiDance.BOTTOM_PANEL,
        tabs = pageTabs + listOf(VisualsTab.tab, MidiTool.tab, ConsoleTool.tab(unseenErrors)),
        count = unseenErrors,
        countTab = ConsoleTool.TAB_ID,
    )
}
