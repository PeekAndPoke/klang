/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.comp.ConsoleTool
import io.peekandpoke.klang.midi.MidiTool
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.ultra.semanticui.SemanticIconFn

/**
 * The bottom panel's tabs on every page: the MIDI tool (KLANG-00402, step 4) and the Console (KLANG-00407), after a
 * page's own tabs, or a panel of their own where a page has none, closed until opened.
 */
object BottomTools {

    /** The panel of a page that has none of its own. */
    const val TITLE = "Tools"

    /**
     * The panel's key wears the same icon on every page, the page's own panel or not (the maintainer, 2026-11-03: "this
     * icon here should always be the same"), the tools the song page's panel had ("please replace it with the other
     * \"tools\" icon that we already had on the song page").
     */
    val ICON: SemanticIconFn = { tools }

    /**
     * [page]'s bottom panel with the tools on every page after its own tabs; the tools alone for a page with none. Its
     * key and the Console's tab count the [unseenErrors]; opened by its key while they count, it opens at the Console.
     */
    fun with(page: KlangWorkbench.Bottom?, unseenErrors: Int): KlangWorkbench.Bottom {
        val tabs = listOf(MidiTool.tab, ConsoleTool.tab(unseenErrors))

        return page?.copy(icon = ICON, tabs = page.tabs + tabs, count = unseenErrors, countTab = ConsoleTool.TAB_ID)
            ?: KlangWorkbench.Bottom(
                title = TITLE,
                icon = ICON,
                tabs = tabs,
                ambient = true,
                count = unseenErrors,
                countTab = ConsoleTool.TAB_ID,
            )
    }
}
