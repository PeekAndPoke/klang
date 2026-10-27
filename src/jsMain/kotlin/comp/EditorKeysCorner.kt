/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.ui.codemirror.KlangEditorKeyBindings
import io.peekandpoke.klang.ui.codemirror.bookmarkRowsOf
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.klang.ui.comp.KlangKeyHint.capsGroups
import io.peekandpoke.klang.ui.comp.KlangKeyHint.panel
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.workshop.keys.KeyBindingsOverlay
import io.peekandpoke.klang.workshop.keys.keyLayer
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.left
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.css.zIndex
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.style

@Suppress("FunctionName")
fun Tag.EditorKeysCorner(editorState: () -> EditorState?) = comp(EditorKeysCorner.Props(editorState = editorState)) {
    EditorKeysCorner(it)
}

/**
 * The editor's keys in its top-right corner while the key hints show (KLANG-00325), over the minimap since it is
 * darker (the maintainer, 2026-10-27: "right aligned in the editor and overlap the minimap"): the song's
 * [KeyBindingsOverlay] of [KlangEditorKeyBindings] and below it the song's bookmarks (maintainer, 2026-10-26), each with
 * the key that jumps there, its line and the code on it (maintainer, 2026-10-25: shown on the hints' key, as
 * Ctrl+digit is the jump), both in Klang's look and right-aligned. It sits in the editor's box, which needs
 * `position: relative`; only an editor that shows it follows the hints (the docs pages' editors do not).
 */
class EditorKeysCorner(ctx: Ctx<Props>) : Component<EditorKeysCorner.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The editor's state now, for its bookmarks; null while the editor is not there yet. */
        val editorState: () -> EditorState?,
    )

    private companion object {
        /** The distance from the editor's top edge. */
        val INSET = 8.px

        /** The space to the editor's right edge, clear of its scroll bar, and between the two panels (maintainer, 2026-10-25). */
        const val GAP_PX = 18
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The key hints show: the panels with them. */
    private val hintsShown by subscribingTo(keys.hints)

    /** Under a modal the panels wait, with the page's keys. */
    private val layer = keyLayer

    private val topLayer by subscribingTo(keys.topLayer)

    /** The keys as they are set now: the bookmarks panel shows the key that jumps to each. */
    private val keymap by subscribingTo(keys.keymap)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            css {
                position = Position.absolute
                top = INSET
                right = GAP_PX.px
                // Never wider than the editor: the panels below keep inside it
                left = INSET
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.flexEnd
                put("gap", "${GAP_PX}px")
                // Over CodeMirror's panels (the search panel is at 300); it never takes a click
                zIndex = 400
                pointerEvents = PointerEvents.none
            }

            // Never wider than the corner: its sections wrap onto a second row in a narrow editor
            div {
                style = "max-width: 100%"
                KeyBindingsOverlay(bindings = KlangEditorKeyBindings, title = "Editor", renderer = KlangKeyHint.overview)
            }

            // Under a modal, and in a page fading out after a navigation (inert), the bookmarks wait. Below the keys,
            // never wider than the corner: the code on a line ends in "…" first
            if (hintsShown && topLayer === layer && dom?.closest("[inert]") == null) {
                div {
                    style = "min-width: 0; max-width: 100%"
                    renderBookmarks()
                }
            }
        }
    }

    /** The song's bookmarks: the key that jumps there, the line, the code on it; nothing when there are none. */
    private fun FlowContent.renderBookmarks() {
        val rows = props.editorState()?.let { bookmarkRowsOf(it) } ?: emptyList()

        if (rows.isEmpty()) {
            return
        }

        // The code's column may shrink; the other two keep their width
        panel(title = "Bookmarks", columns = 3, template = "auto auto minmax(0, 1fr)") {
            rows.forEach { row ->
                // The jump key as the overview shows it: the first binding, keys in a row too
                val jump = KlangEditorKeyBindings.goToBookmark(row.digit)?.let { keymap.bindingsOf(it.id).firstOrNull() }

                span {
                    jump?.let { capsGroups(groups = it.keyGroups(platform = keymap.platform, compact = true)) }
                }

                span { +"line ${row.line}" }

                span {
                    style = listOf(
                        "font-family: monospace",
                        "color: ${KlangTheme.Hex.textPrimary}",
                        "white-space: pre",
                        "min-width: 0",
                        "overflow: hidden",
                        "text-overflow: ellipsis",
                    ).joinToString("; ")

                    +row.code
                }
            }
        }
    }
}
