/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.GutterMarker
import io.peekandpoke.klang.codemirror.ext.Prec
import io.peekandpoke.klang.codemirror.ext.RangeSet
import io.peekandpoke.klang.codemirror.ext.RangeValue
import io.peekandpoke.klang.codemirror.ext.StateEffect
import io.peekandpoke.klang.codemirror.ext.StateEffectType
import io.peekandpoke.klang.codemirror.ext.StateField
import io.peekandpoke.klang.codemirror.ext.StateFieldConfig
import io.peekandpoke.klang.codemirror.ext.Text
import io.peekandpoke.klang.codemirror.ext.Transaction
import io.peekandpoke.klang.codemirror.ext.invertedEffects
import io.peekandpoke.klang.codemirror.ext.isolateHistory
import io.peekandpoke.klang.codemirror.ext.lineNumberMarkers
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.Node
import org.w3c.dom.events.KeyboardEvent

/**
 * Numbered bookmarks as in IntelliJ (KLANG-00323): Ctrl+Shift+digit sets one on the cursor's line, Ctrl+digit goes
 * there.
 *
 * Wires [Bookmarks] into the editor and holds no rule of its own: a state field keeps the bookmarks and maps them
 * through every change, a key handler sets and goes, the line-number gutter shows them, the history carries the line
 * moves and resets into undo and redo, and a listener reports each change to the host ([BookmarkOptions]), which keeps
 * them.
 *
 * The keys are read from the key's position (`event.code`), Ctrl on a Mac too (Cmd+digit is the browser's tab switch
 * there): the number keys above the letters set and go, so Shift+3 on a German layout (`§`) or Shift+1 on a French
 * one (`1`) still sets, and Ctrl+Shift+7 on a German layout (`/`) sets 7 instead of running the comment key. The
 * keypad's digits only go (Shift turns them into the arrow and page keys). A handled key never reaches the browser,
 * also when there is no bookmark to go to.
 */
internal object BookmarksExtension {

    /** How the code of a number key above the letters starts, before its digit (`Digit3`). */
    private const val DIGIT_CODE = "Digit"

    /** How the code of a number key on the numeric keypad starts, before its digit (`Numpad3`). */
    private const val NUMPAD_CODE = "Numpad"

    /**
     * CodeMirror's `assoc` for an offset that stays before the character after it: text inserted at the offset goes
     * before it, so a line break typed before a line's text takes the bookmark down with the text.
     */
    private const val ASSOC_BEFORE_NEXT = 1

    /** The user event of CodeMirror's move line up and down (Alt+Up, Alt+Down). */
    private const val MOVE_LINE_EVENT = "move.line"

    /** The class of a bookmarked line's element in the line-number gutter, which highlights the line number. */
    const val LINE_CLASS = "cm-bookmark"

    /** The class of the bookmark's digit beside the line number. */
    const val DIGIT_CLASS = "cm-bookmark-digit"

    /**
     * The room kept free right of every line number for a bookmark's box, in the line numbers' size: the box and the
     * gap before it ([DIGIT_BOX], [DIGIT_GAP], which are in the digit's smaller size) and a little to spare.
     */
    private const val DIGIT_ROOM = "1.4em"

    /** The width and height of the box around a bookmark's digit, in the digit's size (0.8 of the line numbers'). */
    private const val DIGIT_BOX = "1.3em"

    /** The gap between a line number and its bookmark's box, in the digit's size. */
    private const val DIGIT_GAP = "0.35em"

    /**
     * Keeps every bookmark on its line number through a replacement of the whole document (a song reset through
     * `setCode`), as a reload would, and drops one past the new end. The history carries it into the undo and the
     * redo of that replacement.
     */
    val keepLines: StateEffectType<Unit> = StateEffect.define()

    /** The bookmarks after a set, a move or a removal, carried by a transaction that changes nothing else. */
    private val changed: StateEffectType<Bookmarks> = StateEffect.define()

    /** Lines that move (old line to new line), for the undo and the redo of a move line, which carry no user event. */
    private val lineMoves: StateEffectType<Map<Int, Int>> = StateEffect.define()

    /** The bookmarks of one editor, with the gutter markers they provide. */
    val field: StateField<Bookmarks> = StateField.define(
        jsObject<StateFieldConfig<Bookmarks>> {
            this.create = { Bookmarks() }
            this.update = { bookmarks, tr -> update(bookmarks = bookmarks, tr = tr) }
            this.provide = { self ->
                lineNumberMarkers.compute(arrayOf(self, "doc")) { state -> markers(state) }
            }
        }
    )

    /** The bookmarks of an editor that starts from [options]' bookmarks and reports to it. */
    fun extension(options: BookmarkOptions): Extension {
        val reporter = Reporter(options)

        return arrayOf(
            field.init { state -> Bookmarks.load(lines = options.initial, doc = TextDoc(state.doc)) },
            theme(),
            // Before the keymaps, which would take a typed `/` for the comment key (Ctrl+Shift+7 on a German layout)
            Prec.highest(
                EditorView.domEventHandlers(
                    jsObject<dynamic> {
                        this.keydown = { event: KeyboardEvent, view: EditorView -> onKeyDown(view, event, options) }
                    }
                )
            ),
            invertedEffects.of { tr -> inverted(tr) },
            EditorState.transactionExtender.of { tr -> isolatedIfMoveLine(tr) },
            EditorView.updateListener.of { update -> reporter.report(update.state) },
        ).unsafeCast<Extension>()
    }

    /**
     * The bookmarks' look in the line-number gutter, for the editors that have bookmarks only (the others keep their
     * line numbers as they were).
     *
     * Every line number keeps room on its right for a bookmark's box, so the code does not move when the first
     * bookmark is set. A bookmarked line's cell is a badge (the number bold, gold on an accent tint), and the digit
     * stands in a small cyan box in the room, a little apart from the number and centred in the line. The box takes
     * no width of its own (its right margin undoes its width and its gap), so the number stays right-aligned where it
     * is on every other line.
     */
    private fun theme(): Extension = EditorView.theme(
        jsObject {
            this[".cm-lineNumbers .cm-gutterElement"] = jsObject<dynamic> {
                paddingRight = "calc(3px + $DIGIT_ROOM)"
            }
            this[".cm-lineNumbers .cm-gutterElement.$LINE_CLASS"] = jsObject<dynamic> {
                color = KlangTheme.Hex.gold
                backgroundColor = "${KlangTheme.Hex.accent}38"
                fontWeight = "bold"
            }
            this[".$DIGIT_CLASS"] = jsObject<dynamic> {
                display = "inline-block"
                boxSizing = "border-box"
                width = DIGIT_BOX
                height = DIGIT_BOX
                lineHeight = "calc($DIGIT_BOX - 2px)"
                marginLeft = DIGIT_GAP
                marginRight = "calc(-1 * ($DIGIT_BOX + $DIGIT_GAP))"
                border = "1px solid ${KlangTheme.Hex.excellent}"
                borderRadius = "3px"
                color = KlangTheme.Hex.excellent
                fontSize = "0.8em"
                textAlign = "center"
                verticalAlign = "middle"
            }
        },
        jsObject<dynamic> {},
    )

    /**
     * A move line is an undo step of its own (the history's `isolateHistory`): joined with an edit right after it, its
     * undo could only follow line numbers that the edit moved. Null, adding nothing, for any other transaction.
     */
    private fun isolatedIfMoveLine(tr: Transaction): Any? {
        if (!tr.isUserEvent(MOVE_LINE_EVENT)) {
            return null
        }

        return jsObject<dynamic> { this.annotations = isolateHistory.of("full") }
    }

    /**
     * The digit of a number key, read from its position ([KeyboardEvent.code]: `Digit3`, or `Numpad3` when [keypad]),
     * or null for any other key.
     */
    fun digitOf(code: String, keypad: Boolean): Int? {
        val rest = when {
            code.startsWith(DIGIT_CODE) -> code.removePrefix(DIGIT_CODE)
            keypad && code.startsWith(NUMPAD_CODE) -> code.removePrefix(NUMPAD_CODE)
            else -> return null
        }

        // The keypad's other keys have codes of the same start (`NumpadAdd`, `NumpadEnter`)
        return rest.toIntOrNull()?.takeIf { it in Bookmarks.DIGITS }
    }

    /**
     * Ctrl+Shift+digit sets, Ctrl+digit goes, with neither Alt (AltGr on Windows) nor Meta held. Returns true for these
     * keys, which keeps them from the browser and from the editor's other key bindings.
     */
    private fun onKeyDown(view: EditorView, event: KeyboardEvent, options: BookmarkOptions): Boolean {
        if (!event.ctrlKey || event.altKey || event.metaKey) {
            return false
        }

        val digit = digitOf(code = event.code, keypad = !event.shiftKey) ?: return false

        if (event.shiftKey) {
            set(view = view, digit = digit, askToMove = options.askToMove)
        } else {
            goTo(view = view, digit = digit)
        }

        return true
    }

    /** Bookmark [digit] set on the cursor's line, asking through [askToMove] when it sits on another line. */
    private fun set(
        view: EditorView,
        digit: Int,
        askToMove: (move: BookmarkOptions.Move, answer: (yes: Boolean) -> Unit) -> Unit,
    ) {
        val state = view.state
        val doc = state.doc
        val cursor = state.selection.main.head

        when (val result = bookmarksOf(state).set(digit = digit, at = cursor, doc = TextDoc(doc))) {
            is Bookmarks.SetResult.Changed -> dispatchChanged(view = view, bookmarks = result.bookmarks)

            is Bookmarks.SetResult.AskToMove -> {
                val move = BookmarkOptions.Move(
                    digit = digit,
                    fromLine = doc.lineAt(result.from).number,
                    toLine = doc.lineAt(cursor).number,
                )

                // The question has the keyboard now: Enter must not type into the song, nor a key set again
                view.contentDOM.blur()
                askToMove(move) { yes -> answered(view = view, move = move, yes = yes) }
            }
        }
    }

    /**
     * The user's answer to [move]: moves the bookmark on a yes, and gives the editor the focus back either way, unless
     * the editor is gone by now (the page was left while the question was open).
     */
    private fun answered(view: EditorView, move: BookmarkOptions.Move, yes: Boolean) {
        if (yes) {
            moveTo(view = view, move = move)
        }

        if (view.dom.isConnected) {
            view.focus()
        }
    }

    /** The bookmark of [move] onto its new line, unless the song is shorter by now. */
    private fun moveTo(view: EditorView, move: BookmarkOptions.Move) {
        val state = view.state
        val doc = state.doc

        if (move.toLine > doc.lines) {
            return
        }

        val target = doc.line(move.toLine).from
        val bookmarks = bookmarksOf(state).put(digit = move.digit, at = target, doc = TextDoc(doc))

        dispatchChanged(view = view, bookmarks = bookmarks)
    }

    /** Moves the cursor to the start of bookmark [digit]'s line and scrolls it there; nothing when it is not set. */
    private fun goTo(view: EditorView, digit: Int) {
        val state = view.state
        val pos = bookmarksOf(state).posOf(digit) ?: return
        val lineStart = state.doc.lineAt(pos).from

        view.dispatch(
            state.update(
                jsObject {
                    this.selection = jsObject<dynamic> { this.anchor = lineStart }
                    // The line in the middle of the editor, as the step back to the last edit does (maintainer,
                    // 2026-10-25): the eye finds it there at once
                    this.effects = EditorView.scrollIntoView(lineStart, jsObject<dynamic> { this.y = "center" })
                }
            )
        )
    }

    private fun dispatchChanged(view: EditorView, bookmarks: Bookmarks) {
        view.dispatch(view.state.update(jsObject { this.effects = changed.of(bookmarks) }))
    }

    private fun bookmarksOf(state: EditorState): Bookmarks = state.field(field).unsafeCast<Bookmarks>()

    /** The bookmarks of [state] by digit, each with the code on its line ([previewOf]); empty without any or this extension. */
    fun rowsOf(state: EditorState): List<BookmarkRow> {
        // An editor without the bookmarks extension has no field: no rows
        val bookmarks = state.field(field, false)?.unsafeCast<Bookmarks>() ?: return emptyList()

        // Bookmarks keeps its own by digit
        return bookmarks.lines(TextDoc(state.doc))
            .map { BookmarkRow(digit = it.digit, line = it.line, code = previewOf(state.doc.line(it.line).text)) }
    }

    /** A tab, or a run of two blanks or more, in a preview. */
    private val previewBlanks = Regex("\\s{2,}|\\t")

    /** A line's code for a preview: trimmed, a tab or a run of blanks at most two spaces (an aligned comment). */
    private fun previewOf(text: String): String = text.trim().replace(previewBlanks, "  ")

    private fun update(bookmarks: Bookmarks, tr: Transaction): Bookmarks {
        if (tr.effects.any { it.`is`(keepLines) }) {
            return Bookmarks.load(lines = bookmarks.lines(TextDoc(tr.startState.doc)), doc = TextDoc(tr.newDoc))
        }

        val set = tr.effects.firstOrNull { it.`is`(changed) }

        if (set != null) {
            return set.value.unsafeCast<Bookmarks>()
        }

        val moves = lineMovesOf(tr)

        if (moves != null) {
            return bookmarks.moved(moves = moves, oldDoc = TextDoc(tr.startState.doc), newDoc = TextDoc(tr.newDoc))
        }

        if (tr.changes.empty) {
            return bookmarks
        }

        val deletions = mutableListOf<Bookmarks.Span>()

        tr.changes.iterChanges { fromA, toA, _, _, _ ->
            if (toA > fromA) {
                deletions.add(Bookmarks.Span(from = fromA, to = toA))
            }
        }

        return bookmarks.mapped(
            mapPos = { pos -> tr.changes.mapPos(pos = pos, assoc = ASSOC_BEFORE_NEXT) },
            deletions = deletions,
            oldDoc = TextDoc(tr.startState.doc),
            // The new document, not `tr.state`: the state is still being built while the fields update.
            newDoc = TextDoc(tr.newDoc),
        )
    }

    /**
     * The lines a transaction moves: a move line (Alt+Up, Alt+Down), worked out from the selection it started from,
     * or the undo or redo of one, which carries them. Null for any other transaction.
     *
     * A move line deletes the line it jumps over and inserts it on the block's other side, so a mapping would take
     * that line's bookmark for deleted.
     */
    private fun lineMovesOf(tr: Transaction): Map<Int, Int>? {
        val carried = tr.effects.firstOrNull { it.`is`(lineMoves) }

        if (carried != null) {
            return carried.value.unsafeCast<Map<Int, Int>>()
        }

        if (!tr.isUserEvent(MOVE_LINE_EVENT) || tr.changes.empty) {
            return null
        }

        val doc = TextDoc(tr.startState.doc)
        val selection = tr.startState.selection.ranges.map { Bookmarks.Span(from = it.from, to = it.to) }
        val blocks = Bookmarks.lineBlocks(selection = selection, doc = doc)
        // Moving down, CodeMirror inserts the jumped line where the block starts; moving up, nothing changes there. Not
        // the selection: a block CodeMirror leaves (on the first line, moving up) drops its ranges from the new one.
        val blockStarts = blocks.map { doc.lineStart(it.first) }.toSet()
        var down = false

        tr.changes.iterChanges { fromA, _, _, _, _ ->
            if (fromA in blockStarts) {
                down = true
            }
        }

        return Bookmarks.lineMoves(blocks = blocks, down = down, lineCount = doc.lineCount)
    }

    /** What the history stores with [tr] to replay on its undo: the bookmarks' side of undoing it. */
    private fun inverted(tr: Transaction): Array<StateEffect<*>> {
        if (tr.effects.any { it.`is`(keepLines) }) {
            return arrayOf(keepLines.of(Unit))
        }

        val moves = lineMovesOf(tr) ?: return emptyArray()

        // Undone, every line goes back where it came from
        return arrayOf(lineMoves.of(moves.entries.associate { it.value to it.key }))
    }

    /** The gutter markers of the bookmarks, sorted by position as a range set must be. */
    private fun markers(state: EditorState): RangeSet<GutterMarker> {
        val doc = state.doc

        val ranges = bookmarksOf(state).placed.map { placed ->
            val line = doc.lineAt(placed.pos)

            BookmarkMarker(digit = placed.digit, number = line.number.toString()).range(line.from)
        }.sortedBy { it.from }

        return RangeSet.of(ranges.toTypedArray())
    }

    /**
     * A bookmarked line's number in the line-number gutter, highlighted ([LINE_CLASS]), with the bookmark's digit
     * after it ([DIGIT_CLASS]). It draws, so it takes the place of CodeMirror's own line number. The digit sits in room
     * kept free right of every line number, so the code does not move when a bookmark is set ([theme]).
     */
    private class BookmarkMarker(
        val digit: Int,
        val number: String,
    ) : GutterMarker() {

        override val elementClass: String get() = LINE_CLASS

        override fun eq(other: RangeValue): Boolean {
            // CodeMirror compares only markers of the same class
            val that = other.unsafeCast<BookmarkMarker>()

            return that.digit == digit && that.number == number
        }

        @JsName("toDOM")
        fun toDOM(@Suppress("UNUSED_PARAMETER") view: EditorView): Node {
            val dom = document.createElement("span")

            val digitDom = document.createElement("span")

            digitDom.className = DIGIT_CLASS
            digitDom.textContent = digit.toString()

            dom.setAttribute("title", "Bookmark $digit")
            dom.appendChild(document.createTextNode(number))
            dom.appendChild(digitDom)

            return dom
        }
    }

    /** A CodeMirror document as the rule reads it. */
    private class TextDoc(private val text: Text) : Bookmarks.Doc {

        override val lineCount: Int get() = text.lines

        override fun lineOf(pos: Int): Int = text.lineAt(pos).number

        override fun lineStart(line: Int): Int = text.line(line).from

        override fun lineText(line: Int): String = text.line(line).text
    }

    /** Reports the bookmarks to the host whenever their lines differ from the last report. */
    private class Reporter(private val options: BookmarkOptions) {

        /** The field value of the last look, so an update that left the bookmarks alone costs nothing. */
        private var seen: Bookmarks? = null

        /** What the host holds: what it gave, until the first report. */
        private var reported: List<Bookmarks.Line> = options.initial.sortedBy { it.digit }

        fun report(state: EditorState) {
            val bookmarks = bookmarksOf(state)

            if (bookmarks === seen) {
                return
            }

            seen = bookmarks

            val lines = bookmarks.lines(TextDoc(state.doc))

            if (lines == reported) {
                return
            }

            reported = lines
            options.onChanged(lines)
        }
    }
}
