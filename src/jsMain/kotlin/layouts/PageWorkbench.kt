/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import kotlinx.html.DIV

/**
 * A page's parts in the workbench, in one place (KLANG-00409): its [column][Parts.column], its own tool windows
 * ([Parts.tool]) and its tabs in the bottom panel ([Parts.bottomTabs]). A page writes one call in its body:
 *
 * ```kotlin
 * private val workbench = PageWorkbench(this) {
 *     column { SongColumn(ctrl = ctrl) }
 *     bottomTabs(helpPanel.tabs())
 * }
 * ```
 *
 * The [parts] are built when the [page] mounts and put into [WorkbenchSlots]; when it unmounts, each slot that still
 * holds them is emptied, so a page that came since keeps its own. The panel itself, with the app's tabs, is the
 * layout's ([BottomTools]); a page that gives nothing gets the area's list in the column and the app's tabs.
 */
class PageWorkbench(private val page: Component<*>, private val parts: Parts.() -> Unit) {

    /** What a page puts into the workbench; each call adds to what the page gives. */
    class Parts internal constructor() {
        internal var column: (DIV.() -> Unit)? = null
        internal val tools = mutableListOf<KlangWorkbench.Tool>()
        internal val bottomTabs = mutableListOf<KlangWorkbench.Tab>()

        /** The column's content for the page, in place of the area's list. */
        fun column(content: DIV.() -> Unit) {
            column = content
        }

        /** A tool window of the page's own, after the layout's (the song page: Settings). */
        fun tool(id: String, title: String, icon: SemanticIconFn, chip: String? = null, content: DIV.() -> Unit) {
            tools += KlangWorkbench.Tool(id = id, title = title, icon = icon, chip = chip, content = content)
        }

        /** Tabs of the page's own in the bottom panel, before the app's (the help pages: Quick help, Lexikon). */
        fun bottomTabs(tabs: List<KlangWorkbench.Tab>) {
            bottomTabs += tabs
        }
    }

    /** What this page put into each slot; null for a slot it left alone or emptied. */
    private var filledColumn: (DIV.() -> Unit)? = null
    private var filledTools: List<KlangWorkbench.Tool>? = null
    private var filledBottomTabs: List<KlangWorkbench.Tab>? = null

    init {
        page.lifecycle.onMount { fill() }
        page.lifecycle.onUnmount { empty() }
    }

    private fun fill() {
        val given = Parts().apply(parts)

        given.column?.let { column ->
            filledColumn = column
            WorkbenchSlots.column(column)
        }

        if (given.tools.isNotEmpty()) {
            val tools = given.tools.toList()

            filledTools = tools
            WorkbenchSlots.tools(tools)
        }

        if (given.bottomTabs.isNotEmpty()) {
            val tabs = given.bottomTabs.toList()

            filledBottomTabs = tabs
            WorkbenchSlots.bottomTabs(tabs)
        }
    }

    private fun empty() {
        val column = filledColumn
        val tools = filledTools
        val bottomTabs = filledBottomTabs

        if (column != null && WorkbenchSlots.column() === column) {
            WorkbenchSlots.column(null)
        }

        if (tools != null && WorkbenchSlots.tools() === tools) {
            WorkbenchSlots.tools(emptyList())
        }

        if (bottomTabs != null && WorkbenchSlots.bottomTabs() === bottomTabs) {
            WorkbenchSlots.bottomTabs(emptyList())
        }

        filledColumn = null
        filledTools = null
        filledBottomTabs = null
    }
}
