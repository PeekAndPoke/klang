/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.codemirror.ToolCallSite
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span

/**
 * The bottom panel's help, for a page with code in it (KLANG-00349, KLANG-00365, KLANG-00370): its tabs Quick help and
 * Lexikon, what each shows, and the calls that fill them. The song page and a tutorial both use it; [pageKey] keeps one
 * page's parts apart from the next one's. Given [toolContext] (the song page), a third tab, Sprudel, shows the tool of
 * the call the editor's cursor is in (KLANG-00374).
 */
class QuickHelpPanel(
    private val pageKey: String,
    private val toolContext: ((ToolCallSite) -> KlangUiToolContext?)? = null,
) {

    companion object {
        /** The bottom panel's tabs, by the ids the workbench keeps with its layout. */
        const val TAB_QUICK_HELP = "quick-help"
        const val TAB_LEXIKON = "lexikon"
        const val TAB_SPRUDEL = "sprudel"
    }

    /** The call around the editor's cursor whose tool the Sprudel tab shows; null outside one. */
    private val toolCall = StreamSource<ToolCallSite?>(null)

    /** The call and parameter last shown, so only a new one turns the panel to the Sprudel tab. */
    private var lastCallKey: String? = null

    /** The name Alt+Q or a hover last asked about; null before the first. */
    private val help = StreamSource<HelpWindow.Entry?>(null)

    /** The Lexikon term the Lexikon tab shows beside its list of terms; null for none yet. */
    private val lexikonSlug = StreamSource<String?>(null)

    /** Where a use clicked in the "Used on" list goes: the code the last name came from. */
    private var jump: (pos: Int) -> Unit = {}

    /** The panel for the workbench's bottom slot. */
    fun bottom(): KlangWorkbench.Bottom = KlangWorkbench.Bottom(
        title = "Help",
        icon = { question_circle },
        tabs = listOf(
            KlangWorkbench.Tab(id = TAB_QUICK_HELP, title = "Quick help") {
                div {
                    key = "help-$pageKey"
                    // The tab fills the panel's height; a long help ends with the panel's own air below it
                    css { put("padding-bottom", "16px") }
                    HelpWindow(
                        entry = help,
                        onJump = { pos -> jump(pos) },
                        options = { renderOptions() },
                        onLexikon = { slug -> showLexikon(slug) },
                    )
                }
            },
            KlangWorkbench.Tab(id = TAB_LEXIKON, title = "Lexikon") {
                div {
                    key = "lexikon-$pageKey"
                    css { put("height", "100%") }
                    LexikonPanel(slug = lexikonSlug, onPick = { lexikonSlug(it) })
                }
            },
        ) + listOfNotNull(
            toolContext?.let { contextOf ->
                KlangWorkbench.Tab(id = TAB_SPRUDEL, title = "Sprudel") {
                    div {
                        key = "sprudel-$pageKey"
                        SprudelToolTab(site = toolCall, contextOf = contextOf)
                    }
                }
            },
        ),
    )

    /**
     * A name's help in the Quick help, the panel opened if it is closed and turned to the Quick help's tab; the focus
     * stays where it is. Alt+Q and a hover alike: the hover comes only with "Show on hover" switched on, and then it is
     * the help the user asked for (the maintainer, 2026-10-27: "When the Lexikon tool is active and the "Show on hover"
     * for the docs then the tool should switch to the docs"; before, a hover left the Lexikon in place, KLANG-00365).
     * [jumpTo] puts the cursor in the code the name came from, for its "Used on" list.
     */
    fun showHelp(entry: HelpWindow.Entry, jumpTo: (pos: Int) -> Unit) {
        jump = jumpTo
        help(entry)
        WorkbenchSlots.workbench { it.showBottom(tab = TAB_QUICK_HELP) }
    }

    /**
     * A Lexikon term in the Lexikon tab, the user staying in the code (the maintainer, 2026-10-27: a Lexikon link
     * "would lead to the Lexikon tab", KLANG-00365).
     */
    fun showLexikon(slug: String) {
        lexikonSlug(slug)
        WorkbenchSlots.workbench { it.showBottom(tab = TAB_LEXIKON) }
    }

    /**
     * The call the editor's cursor is now in, for the Sprudel tab; null outside any call with a tool. A new call
     * turns the panel to the tab (the maintainer, 2026-10-27: "Whenever the cursor is inside the parenteses if a
     * function that supports tools we should so the tool"); the cursor leaving one leaves the panel as it is.
     */
    fun showToolCall(site: ToolCallSite?) {
        toolCall(site)

        // A new call or parameter; an edit inside the same call (it starts the tool over) leaves the panel be
        if (site != null && toolContext != null && site.callKey != lastCallKey) {
            WorkbenchSlots.workbench { it.showBottom(tab = TAB_SPRUDEL) }
        }

        lastCallKey = site?.callKey
    }

    /** The Sprudel tab, the panel opened if it is closed: a right-click's "Open the Sprudel tool" (KLANG-00374). */
    fun showSprudel() {
        if (toolContext != null) {
            WorkbenchSlots.workbench { it.showBottom(tab = TAB_SPRUDEL) }
        }
    }

    /** The quick help's options, at the right of its headline: how to call it, and the hover's switch. */
    private fun FlowContent.renderOptions() {
        span {
            css {
                put("font-size", "12px")
                put("color", "var(--kl-ink-label)")
                put("white-space", "nowrap")
            }

            +"In the code: "
            // The same caps as every key hint, "Esc" in the close pill beside it included (the maintainer, 2026-10-27)
            with(KlangKeyHint) { caps(parts = listOf("Alt", "Q")) }
            +" on a name, or rest the mouse on it"
        }

        HoverHelpToggle()
    }
}
