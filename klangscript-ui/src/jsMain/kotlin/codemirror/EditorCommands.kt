/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.Coords
import io.peekandpoke.klang.codemirror.ext.EditorSelection
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.Prec
import io.peekandpoke.klang.codemirror.ext.ViewUpdate
import io.peekandpoke.klang.codemirror.ext.indentRange
import io.peekandpoke.klang.codemirror.ext.indentSelection
import io.peekandpoke.klang.codemirror.ext.openSearchPanel
import io.peekandpoke.klang.codemirror.ext.toggleBlockComment
import io.peekandpoke.kraft.utils.jsObject
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.MouseEvent

/**
 * The editor's commands that CodeMirror does not bring as the key map's table wants them (KLANG-00325 step 3b), and
 * Alt+Shift+click, which adds a cursor.
 */
internal object EditorCommands {

    /** The empty block comment written where nothing is selected; the cursor goes between its halves. */
    private const val EMPTY_BLOCK_COMMENT = "/**/"

    /**
     * Wraps each selection in a block comment, or unwraps one that is (CodeMirror's toggle); with nothing selected
     * writes an empty block comment and puts the cursor inside it.
     */
    val blockComment: Command = { view ->
        val state = view.state

        if (state.selection.ranges.all { it.empty }) {
            val spec = state.changeByRange { range ->
                jsObject<dynamic> {
                    this.changes = jsObject<dynamic> {
                        this.from = range.from
                        this.insert = EMPTY_BLOCK_COMMENT
                    }
                    this.range = EditorSelection.cursor(range.from + EMPTY_BLOCK_COMMENT.length / 2)
                }
            }

            view.dispatch(state.update(spec.unsafeCast<dynamic>(), jsObject { this.userEvent = "input" }))
            true
        } else {
            toggleBlockComment(view)
        }
    }

    /**
     * Takes back the occurrence that select next occurrence added last; nothing with one selection only.
     *
     * CodeMirror keeps the first selection the main one and adds each next occurrence after the last, wrapping to the
     * document's start, so the newest is the one before the main one, counted round: the last one, or after a wrap the
     * one just before the main one. The view scrolls to the one that is the newest now.
     */
    val unselectOccurrence: Command = { view ->
        val selection = view.state.selection
        val count = selection.ranges.size

        if (count < 2) {
            false
        } else {
            val main = selection.mainIndex
            val newest = (main - 1 + count) % count
            val nowNewest = selection.ranges[(newest - 1 + count) % count]
            val rest = selection.ranges.filterIndexed { index, _ -> index != newest }.toTypedArray()

            view.dispatch(
                jsObject<dynamic> {
                    this.selection = EditorSelection.create(ranges = rest, mainIndex = if (newest < main) main - 1 else main)
                    this.effects = EditorView.scrollIntoView(nowNewest.to)
                }
            )
            true
        }
    }

    /** Opens the search panel with the cursor in its replace field (Ctrl+R; the browser's reload stays away). */
    val replace: Command = { view ->
        openSearchPanel(view)

        // Selecting its text also gives the field the focus
        (view.dom.querySelector(".cm-search input[name=replace]") as? HTMLInputElement)?.select()

        true
    }

    /** Reindents the selected lines; with nothing selected the whole document, the cursor staying where it is. */
    val reformat: Command = { view ->
        val state = view.state

        if (state.selection.ranges.all { it.empty }) {
            val changes = indentRange(state = state, from = 0, to = state.doc.length)

            if (!changes.empty) {
                view.dispatch(
                    jsObject<dynamic> {
                        this.changes = changes
                        this.userEvent = "indent"
                    }
                )
            }

            true
        } else {
            indentSelection(view)
        }
    }

    /**
     * Alt+Shift+click adds a cursor where it clicks (Option+Shift+click on a Mac); Ctrl+click no longer does, it goes
     * to a definition. CodeMirror's own press would take Shift for extending the selection, so this press has its own
     * selection style, before the rectangle selection that Alt+drag starts.
     */
    val addCursorByClick: Extension = arrayOf(
        EditorView.clickAddsSelectionRange.of { false },
        Prec.high(
            EditorView.mouseSelectionStyle.of { view, event ->
                if (event.altKey && event.shiftKey && event.button.toInt() == 0) addCursorStyle(view) else null
            }
        ),
    ).unsafeCast<Extension>()

    /** The selection when the press started, with a cursor added where the mouse is. */
    private fun addCursorStyle(view: EditorView): dynamic {
        var start = view.state.selection

        return jsObject<dynamic> {
            this.get = { event: MouseEvent, _: Boolean, _: Boolean ->
                val coords = jsObject<dynamic> {
                    this.x = event.clientX
                    this.y = event.clientY
                }
                val pos = view.posAtCoords(coords.unsafeCast<Coords>())

                pos?.let { start.addRange(range = EditorSelection.cursor(it)) } ?: start
            }
            this.update = { update: ViewUpdate ->
                if (update.docChanged) {
                    start = start.map(update.changes)
                }

                false
            }
        }
    }
}
