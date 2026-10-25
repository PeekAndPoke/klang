/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.ui.codemirror.KlangEditorKeyBindings
import io.peekandpoke.klang.ui.comp.KlangKeyHint
import io.peekandpoke.klang.workshop.keys.KeyBindingsOverlay
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.css.zIndex
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.HTMLElement
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.EditorKeysCorner() = comp {
    EditorKeysCorner(it)
}

/**
 * The editor's keys in its top-right corner while the key hints show (KLANG-00325): a [KeyBindingsOverlay] of
 * [KlangEditorKeyBindings] in Klang's look, left of the minimap. It sits in the editor's box, which needs
 * `position: relative`; only an editor that shows it follows the hints (the docs pages' editors do not).
 */
class EditorKeysCorner(ctx: NoProps) : PureComponent(ctx) {

    private companion object {
        /** The distance from the editor's top edge. */
        val INSET = 8.px

        /** The space to the minimap on its right (maintainer, 2026-10-25). */
        const val GAP_PX = 18

        /** The minimap's element, which CodeMirror's minimap extension mounts at the editor's right edge. */
        const val MINIMAP_SELECTOR = ".cm-minimap-gutter"
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The key hints show: the overview with them, measured against the minimap as it is now. */
    private val hintsShown by subscribingTo(keys.hints)

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
                // Over CodeMirror's panels (the search panel is at 300); it never takes a click
                zIndex = 400
                pointerEvents = PointerEvents.none
            }

            KeyBindingsOverlay(bindings = KlangEditorKeyBindings, title = "Editor", renderer = KlangKeyHint.overview)
        }
    }
}
