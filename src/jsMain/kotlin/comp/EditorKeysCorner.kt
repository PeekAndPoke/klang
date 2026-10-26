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
import kotlinx.css.JustifyContent
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.display
import kotlinx.css.justifyContent
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
import org.w3c.dom.HTMLElement
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.EditorKeysCorner(editorState: () -> EditorState?) = comp(EditorKeysCorner.Props(editorState = editorState)) {
    EditorKeysCorner(it)
}

/**
 * The editor's keys in its top-right corner while the key hints show (KLANG-00325), left of the minimap: the song's
 * bookmarks (each with the key that jumps there, its line and the code on it; maintainer, 2026-10-25: shown on the
 * hints' key, as Ctrl+digit is the jump), and right of them a [KeyBindingsOverlay] of [KlangEditorKeyBindings], both
 * in Klang's look. It sits in the editor's box, which needs `position: relative`; only an editor
 * that shows it follows the hints (the docs pages' editors do not).
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

        /** The space to the minimap on its right, and between the two panels (maintainer, 2026-10-25). */
        const val GAP_PX = 18

        /** The minimap's element, which CodeMirror's minimap extension mounts at the editor's right edge. */
        const val MINIMAP_SELECTOR = ".cm-minimap-gutter"

    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The key hints show: the panels with them, measured against the minimap as it is now. */
    private val hintsShown by subscribingTo(keys.hints)

    /** Under a modal the panels wait, with the page's keys. */
    private val layer = keyLayer

    private val topLayer by subscribingTo(keys.topLayer)

    /** The keys as they are set now: the bookmarks panel shows the key that jumps to each. */
    private val keymap by subscribingTo(keys.keymap)

    /** How far the minimap reaches in from the editor's right edge (with the scroll bar beside it), as last measured. */
    private var minimapWidth by value(0)

    init {
        lifecycle {
            // Mounted while the hints already show: measured at once, not at the next update
            onMount {
                measureMinimap()
            }

            onUpdate {
                measureMinimap()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Reads how far the minimap reaches in from the editor's right edge, only while the hints show (no layout on every
     * keystroke); a new value redraws, the same one does nothing (no loop with onUpdate).
     */
    private fun measureMinimap() {
        if (!hintsShown) {
            return
        }

        val editor = dom?.parentElement ?: return
        val minimap = editor.querySelector(MINIMAP_SELECTOR) as? HTMLElement
        val width = minimap?.let { (editor.getBoundingClientRect().right - it.getBoundingClientRect().left).roundToInt() } ?: 0

        if (width != minimapWidth) {
            minimapWidth = width
        }
    }

    override fun VDom.render() {
        div {
            css {
                position = Position.absolute
                top = INSET
                right = (minimapWidth + GAP_PX).px
                // Never wider than the editor: the bookmarks panel shrinks (its code ends in "…") before it leaves it
                left = INSET
                display = Display.flex
                justifyContent = JustifyContent.flexEnd
                alignItems = Align.flexStart
                put("gap", "${GAP_PX}px")
                // Over CodeMirror's panels (the search panel is at 300); it never takes a click
                zIndex = 400
                pointerEvents = PointerEvents.none
            }

            // Under a modal, and in a page fading out after a navigation (inert), the bookmarks wait
            if (hintsShown && topLayer === layer && dom?.closest("[inert]") == null) {
                // At most 40% of the corner, so the keys overview keeps room for its sections side by side
                div {
                    style = "min-width: 0; max-width: 40%; flex: 0 1 auto"
                    renderBookmarks()
                }
            }

            // May shrink: its sections then wrap onto a second row, the widest section the least it takes (its min-content,
            // which a min-width of 0 would give away)
            div {
                style = "flex: 0 1 auto"
                KeyBindingsOverlay(bindings = KlangEditorKeyBindings, title = "Editor", renderer = KlangKeyHint.overview)
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
