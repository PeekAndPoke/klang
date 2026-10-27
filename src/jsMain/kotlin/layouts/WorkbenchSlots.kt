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
 * What a page puts into the workbench while it is shown (KLANG-00349): the column's content and tool windows of its
 * own. The page fills them when it mounts and empties them when it unmounts; the `WorkbenchLayout` reads them. A page
 * that fills nothing gets the area's list in the column and the layout's tool windows only.
 */
object WorkbenchSlots {

    /** The column for the page (the song page: the song); null for the area's list. */
    val column = StreamSource<(DIV.() -> Unit)?>(null)

    /** The page's own tool windows, after the layout's (the song page: Settings). */
    val tools = StreamSource<List<KlangWorkbench.Tool>>(emptyList())

    /** The page's bottom panel (the song page: Help, wide enough for signatures and parameters); null for none. */
    val bottom = StreamSource<KlangWorkbench.Bottom?>(null)

    /** The workbench drawn now, so a page can show its panels by a key (Alt+Q shows the Help). */
    val workbench = ComponentRef.Tracker<KlangWorkbench>()

    /** Empties the slots: the page that filled them is gone. */
    fun clear() {
        column(null)
        tools(emptyList())
        bottom(null)
    }
}
