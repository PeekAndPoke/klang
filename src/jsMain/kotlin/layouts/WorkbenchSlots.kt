/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.DIV

/**
 * What a page puts into the workbench while it is shown (KLANG-00349): the column's content, tool windows and bottom
 * panel tabs of its own; the `WorkbenchLayout` reads them. A page fills them through [PageWorkbench] (KLANG-00409),
 * which fills them when it mounts and empties them when it unmounts. A page that fills nothing gets the area's list in
 * the column, the layout's tool windows and the app's tabs ([BottomTools]) only.
 */
object WorkbenchSlots {

    /** The column for the page (the song page: the song); null for the area's list. */
    val column = StreamSource<(DIV.() -> Unit)?>(null)

    /** The page's own tool windows, after the layout's (the song page: Settings). */
    val tools = StreamSource<List<KlangWorkbench.Tool>>(emptyList())

    /** The page's own tabs in the bottom panel, before the app's (the help pages: Quick help, Lexikon); empty for none. */
    val bottomTabs = StreamSource<List<KlangWorkbench.Tab>>(emptyList())

    /** The workbench drawn now, so a page can show its panels by a key (Alt+Q shows the Help). */
    val workbench = ComponentRef.Tracker<KlangWorkbench>()

    /** Empties the slots; for the specs, as a page empties only its own parts ([PageWorkbench]). */
    fun clear() {
        column(null)
        tools(emptyList())
        bottomTabs(emptyList())
    }
}
