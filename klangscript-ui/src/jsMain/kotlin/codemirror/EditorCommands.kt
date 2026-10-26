/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.Coords
import io.peekandpoke.klang.codemirror.ext.EditorSelection
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.Prec
import io.peekandpoke.klang.codemirror.ext.SelectionRange
import io.peekandpoke.klang.codemirror.ext.ViewUpdate
import io.peekandpoke.klang.codemirror.ext.indentRange
import io.peekandpoke.klang.codemirror.ext.indentSelection
import io.peekandpoke.klang.codemirror.ext.openSearchPanel
import io.peekandpoke.klang.codemirror.ext.syntaxTree
import io.peekandpoke.klang.codemirror.ext.toggleBlockComment
import io.peekandpoke.kraft.utils.jsObject
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.MouseEvent

/**
 * The editor's commands that CodeMirror does not bring as the key map's table wants them (KLANG-00325 step 3b), and
 * Alt+Shift+click, which adds a cursor.
 */
internal object EditorCommands {

    /** A block comment's opening half. */
    private const val OPEN = "/*"

    /** A block comment's closing half. */
    private const val CLOSE = "*/"

    /** The comment written where a cursor is outside any. */
    private const val EMPTY_BLOCK_COMMENT = OPEN + CLOSE

    /** The syntax tree's name of a block comment (`@lezer/javascript`). */
    private const val BLOCK_COMMENT_NODE = "BlockComment"

    /**
     * Comments out, or back in, per selection: a selected range is wrapped in a block comment, or unwrapped when it is
     * one (CodeMirror's toggle, when every range is selected text); a cursor inside a block comment unwraps that comment
     * (an empty one `/**/` goes away, so a second press takes back the first; an unclosed one loses its opening
     * only); a cursor outside any writes an empty one and goes between its halves.
     *
     * The syntax tree is the one parsed so far: on a very long song a cursor beyond it is read as outside any comment.
     */
    val blockComment: Command = { view ->
        val state = view.state

        when {
            state.readOnly -> false

            state.selection.ranges.none { it.empty } -> toggleBlockComment(view)

            else -> {
                val spec = state.changeByRange { range -> blockCommentAt(state = state, range = range) }

                view.dispatch(state.update(spec, jsObject { this.userEvent = "input" }))
                true
            }
        }
    }

    /** The change and the new range of [blockComment] for one [range] of a selection that holds a cursor. */
    private fun blockCommentAt(state: EditorState, range: SelectionRange): dynamic {
        val pos = range.from
        val comment = syntaxTree(state).resolve(pos, 1).takeIf { node ->
            range.empty && node.name == BLOCK_COMMENT_NODE && node.from < pos && pos < node.to
        }
        // Selected text next to the cursors, wrapped as CodeMirror's toggle writes it
        val wrapped = state.sliceDoc(range.from - OPEN.length - 1, range.from) == "$OPEN " &&
            state.sliceDoc(range.to, range.to + CLOSE.length + 1) == " $CLOSE"

        return when {
            !range.empty && wrapped -> jsObject<dynamic> {
                this.changes = arrayOf(
                    change(from = range.from - OPEN.length - 1, to = range.from),
                    change(from = range.to, to = range.to + CLOSE.length + 1),
                )
                this.range = EditorSelection.range(anchor = range.anchor - OPEN.length - 1, head = range.head - OPEN.length - 1)
            }

            !range.empty -> jsObject<dynamic> {
                this.changes = arrayOf(change(from = range.from, insert = "$OPEN "), change(from = range.to, insert = " $CLOSE"))
                this.range = EditorSelection.range(anchor = range.anchor + OPEN.length + 1, head = range.head + OPEN.length + 1)
            }

            comment != null -> unwrap(state = state, from = comment.from, to = comment.to, cursor = pos)

            else -> jsObject<dynamic> {
                this.changes = change(from = pos, insert = EMPTY_BLOCK_COMMENT)
                this.range = EditorSelection.cursor(pos + OPEN.length)
            }
        }
    }

    /**
     * Takes the halves off the block comment from [from] to [to], with one space inside each and a doc comment's
     * second star (`/** doc */` gives `doc`); the cursor stays on its text. An unclosed comment (the parser lets it run
     * to the document's end) loses its opening only, never the document's last characters.
     */
    private fun unwrap(state: EditorState, from: Int, to: Int, cursor: Int): dynamic {
        fun at(pos: Int, text: String) = state.sliceDoc(pos, pos + text.length) == text

        val closed = to - from >= EMPTY_BLOCK_COMMENT.length && at(pos = to - CLOSE.length, text = CLOSE)
        val docStar = to - from > EMPTY_BLOCK_COMMENT.length && at(pos = from + OPEN.length, text = "*")
        val openHalf = from + OPEN.length + (if (docStar) 1 else 0)
        val openEnd = openHalf + (if (at(pos = openHalf, text = " ")) 1 else 0)
        val closeHalf = if (closed) to - CLOSE.length else to
        val closeStart = closeHalf - (if (closed && at(pos = closeHalf - 1, text = " ")) 1 else 0)

        // `/* */`: the two spaces are one, and nothing is inside
        if (openEnd > closeStart) {
            return jsObject<dynamic> {
                this.changes = change(from = from, to = to)
                this.range = EditorSelection.cursor(from)
            }
        }

        return jsObject<dynamic> {
            this.changes = arrayOf(change(from = from, to = openEnd), change(from = closeStart, to = to))
            this.range = EditorSelection.cursor(cursor.coerceIn(minimumValue = openEnd, maximumValue = closeStart) - (openEnd - from))
        }
    }

    private fun change(from: Int, to: Int = from, insert: String = ""): dynamic = jsObject<dynamic> {
        this.from = from
        this.to = to
        this.insert = insert
    }

    /**
     * Takes back the occurrence that select next occurrence added last; nothing with one selection only.
     *
     * CodeMirror keeps the first selection the main one and adds each next occurrence after the last, wrapping to the
     * document's start, so the newest is the one before the main one, counted round: the last one, or after a wrap the
     * one just before the main one. Cursors added otherwise (above, below, by Alt+Shift+click) become the main one
     * themselves, so where the ranges are not all one selected text, the main one is the newest. The view scrolls to
     * the one that is the newest now.
     */
    val unselectOccurrence: Command = { view ->
        val state = view.state
        val selection = state.selection
        val ranges = selection.ranges
        val count = ranges.size

        if (count < 2) {
            false
        } else {
            val main = selection.mainIndex
            val text = state.sliceDoc(ranges[0].from, ranges[0].to)
            val bySelectNext = ranges.none { it.empty } && ranges.all { state.sliceDoc(it.from, it.to) == text }
            val newest = if (bySelectNext) (main - 1 + count) % count else main
            val rest = without(selection = selection, index = newest)
            val nowNewest = if (bySelectNext) ranges[(newest - 1 + count) % count] else rest.main

            view.dispatch(
                jsObject<dynamic> {
                    this.selection = rest
                    this.effects = EditorView.scrollIntoView(nowNewest.to)
                }
            )
            true
        }
    }

    /**
     * [selection] without its range at [index]; when that was the main one, its neighbour is the main one now, the one
     * before it, or after it at the start (after add cursor above, the cursor below the one taken away).
     */
    private fun without(selection: EditorSelection, index: Int): EditorSelection {
        val main = selection.mainIndex
        val keep = when {
            index != main -> main
            main == 0 -> 1
            else -> main - 1
        }
        val rest = selection.ranges.filterIndexed { i, _ -> i != index }.toTypedArray()

        return EditorSelection.create(ranges = rest, mainIndex = if (keep > index) keep - 1 else keep)
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

        if (state.readOnly) {
            false
        } else if (state.selection.ranges.all { it.empty }) {
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
     * Alt+Shift+click adds a cursor where it clicks (Option+Shift+click on a Mac), and Alt+Shift+drag a selected range;
     * Alt+Shift+click on one of several cursors takes it away. Ctrl+click no longer adds a cursor, it goes to a
     * definition. CodeMirror's own press would take Shift for extending the selection, so this press has its own
     * selection style, before the rectangle selection that Alt+drag starts.
     */
    val addCursorByClick: Extension = arrayOf(
        EditorView.clickAddsSelectionRange.of { false },
        Prec.high(
            EditorView.mouseSelectionStyle.of { view, event ->
                if (event.altKey && event.shiftKey && event.button.toInt() == 0) addCursorStyle(view = view, press = event) else null
            }
        ),
    ).unsafeCast<Extension>()

    /**
     * The selection when the [press] started, with a range added from the press to where the mouse is now; a click (no
     * move) on one of several ranges takes that range away instead. A press inside the main selection's text starts
     * the browser's drag of that text, as without Alt+Shift (CodeMirror leaves such a press to the browser).
     */
    private fun addCursorStyle(view: EditorView, press: MouseEvent): dynamic {
        fun posOf(event: MouseEvent): Int? = view.posAtCoords(
            jsObject<dynamic> {
                this.x = event.clientX
                this.y = event.clientY
            }.unsafeCast<Coords>()
        )

        var start = view.state.selection
        var pressedAt: Int? = posOf(press)

        return jsObject<dynamic> {
            // CodeMirror calls it on the press (or, for a press inside the selected text, on the release) and on every move
            this.get = get@{ event: MouseEvent, _: Boolean, _: Boolean ->
                val pos = posOf(event) ?: return@get start
                val from = pressedAt ?: pos.also { pressedAt = it }
                val clickedOn = start.ranges.indexOfFirst { it.from <= pos && pos <= it.to }

                if (pos == from && start.ranges.size > 1 && clickedOn >= 0) {
                    without(selection = start, index = clickedOn)
                } else {
                    start.addRange(range = EditorSelection.range(anchor = from, head = pos))
                }
            }
            this.update = { update: ViewUpdate ->
                if (update.docChanged) {
                    start = start.map(update.changes)
                    pressedAt = pressedAt?.let { update.changes.mapPos(it) }
                }

                false
            }
        }
    }
}
