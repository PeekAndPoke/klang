/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorSelection
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.basicSetup
import io.peekandpoke.klang.codemirror.ext.deleteCharBackward
import io.peekandpoke.klang.codemirror.ext.deleteLine
import io.peekandpoke.klang.codemirror.ext.history
import io.peekandpoke.klang.codemirror.ext.insertNewlineAndIndent
import io.peekandpoke.klang.codemirror.ext.javascript
import io.peekandpoke.klang.codemirror.ext.lineNumbers
import io.peekandpoke.klang.codemirror.ext.moveLineDown
import io.peekandpoke.klang.codemirror.ext.moveLineUp
import io.peekandpoke.klang.codemirror.ext.redo
import io.peekandpoke.klang.codemirror.ext.undo
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.asList
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * The wiring of the numbered bookmarks into CodeMirror (KLANG-00323), on a real editor state and view.
 *
 * [BookmarksSpec] holds the rule. This spec holds what the rule cannot see: that the keys set and go by the key's
 * position, before the editor's own key bindings, and never reach the browser; that a move asks the host, gives it
 * the keyboard, and waits for its answer; that real commands (Enter, Backspace, delete line, move line, a paste) and
 * the history's undo and redo keep the bookmarks on their lines; that every change is reported; that the gutter shows
 * the bookmarks; and that the editor starts from the host's bookmarks and keeps them through a replacement of the
 * whole document.
 */
class BookmarksExtensionSpec : StringSpec({

    val song = (1..40).joinToString("\n") { "line $it" }
    val indented = (1..40).joinToString("\n") { "    line $it" }

    /** What Shift makes of the digits on a US layout. */
    val usShifted = listOf(")", "!", "@", "#", "$", "%", "^", "&", "*", "(")

    fun newState(host: RecordingHost, vararg extra: Any, doc: String = song): EditorState = EditorState.create(
        jsObject<EditorStateConfig> {
            this.doc = doc
            // The others first, as the editor has `basicSetup` before the bookmarks
            this.extensions = arrayOf<Any>(*extra, BookmarksExtension.extension(host.options))
                .unsafeCast<Array<Extension>>()
        }
    )

    fun EditorState.bookmarkLines(): List<Bookmarks.Line> =
        field(BookmarksExtension.field).unsafeCast<Bookmarks>().placed.map {
            Bookmarks.Line(digit = it.digit, line = doc.lineAt(it.pos).number)
        }

    /** Replaces the text from [from] to [to] with [text], as typing, a paste or a delete does. */
    fun EditorView.change(from: Int, to: Int = from, text: String = "") {
        dispatch(
            state.update(
                jsObject {
                    this.changes = jsObject<dynamic> {
                        this.from = from
                        this.to = to
                        this.insert = text
                    }
                }
            )
        )
    }

    /** Runs [block] on a view of [state] in the page, and removes the view after. */
    suspend fun withView(state: EditorState, block: suspend (EditorView) -> Unit) {
        val parent = document.createElement("div")

        document.body?.appendChild(parent)

        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = state
                this.parent = parent
            }
        )

        try {
            block(view)
        } finally {
            view.destroy()
            parent.remove()
        }
    }

    /** Puts the cursor [column] characters into [line]. */
    fun EditorView.moveCursorTo(line: Int, column: Int = 0) {
        val at = state.doc.line(line).from + column

        dispatch(state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = at } }))
    }

    fun EditorView.cursorLine(): Int = state.doc.lineAt(state.selection.main.head).number

    /** Presses the key at [code] with Ctrl and the other modifiers asked for; [key] is what the layout makes of it. */
    fun EditorView.press(
        code: String,
        key: String,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = true,
        meta: Boolean = false,
    ): KeyboardEvent {
        val init = jsObject<KeyboardEventInit> {
            this.code = code
            this.key = key
            this.ctrlKey = ctrl
            this.shiftKey = shift
            this.altKey = alt
            this.metaKey = meta
            this.bubbles = true
            this.cancelable = true
        }

        val event = KeyboardEvent("keydown", init)

        contentDOM.dispatchEvent(event)

        return event
    }

    /** Ctrl+Shift+[digit] on a US layout. */
    fun EditorView.pressSet(digit: Int): KeyboardEvent =
        press(code = "Digit$digit", key = usShifted[digit], shift = true)

    /** Ctrl+[digit] above the letters. */
    fun EditorView.pressGo(digit: Int): KeyboardEvent = press(code = "Digit$digit", key = "$digit")

    "Ctrl+Shift+digit sets on the cursor's line, Ctrl+digit goes there, and neither reaches the browser" {
        val host = RecordingHost()

        // Indented, so the start of a line is not where its text starts
        withView(newState(host, doc = indented)) { view ->
            view.moveCursorTo(line = 10, column = 4)
            // Shift+3 types a `§` on a German layout: the digit comes from the key's position
            val set = view.press(code = "Digit3", key = "§", shift = true)
            val afterSet = host.reports.lastOrNull()

            view.moveCursorTo(line = 30, column = 4)
            val go = view.pressGo(3)
            val wentTo = view.state.selection.main.head

            val unset = view.pressGo(5)
            val stayedAt = view.state.selection.main.head

            withClue("the set") {
                afterSet shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
                set.defaultPrevented shouldBe true
            }

            withClue("the go, to the start of the line") {
                wentTo shouldBe view.state.doc.line(10).from
                go.defaultPrevented shouldBe true
            }

            withClue("a go to a digit that is not set") {
                stayedAt shouldBe wentTo
                unset.defaultPrevented shouldBe true
            }
        }
    }

    "on the keypad Ctrl+digit goes but Ctrl+Shift+digit does not set, and a key with another modifier is left alone" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.moveCursorTo(line = 20)
            view.pressSet(7)
            view.moveCursorTo(line = 5)
            // With Num Lock on
            val keypad = view.press(code = "Numpad7", key = "7")
            val wentTo = view.cursorLine()

            // Shift turns the keypad's 7 into Home
            val keypadSet = view.press(code = "Numpad7", key = "Home", shift = true)
            // AltGr is Ctrl+Alt on Windows
            val altGr = view.press(code = "Digit2", key = "²", shift = true, alt = true)
            val noCtrl = view.press(code = "Digit2", key = "@", shift = true, ctrl = false)
            val withMeta = view.press(code = "Digit2", key = "2", meta = true)
            val letter = view.press(code = "KeyB", key = "B", shift = true)

            withClue("the keypad") {
                wentTo shouldBe 20
                keypad.defaultPrevented shouldBe true
            }

            withClue("other keys") {
                host.reports.size shouldBe 1
                keypadSet.defaultPrevented shouldBe false
                altGr.defaultPrevented shouldBe false
                noCtrl.defaultPrevented shouldBe false
                withMeta.defaultPrevented shouldBe false
                letter.defaultPrevented shouldBe false
            }
        }
    }

    "the keys come before the editor's own: Ctrl+Shift+7 on a German layout sets 7 and does not comment the line" {
        val host = RecordingHost()

        withView(newState(host, basicSetup, javascript())) { view ->
            view.moveCursorTo(line = 10)
            // Shift+7 types a `/` on a German layout, and Ctrl+/ is the comment key
            val set = view.press(code = "Digit7", key = "/", shift = true)

            withClue("bookmark 7 set, the line as it was") {
                host.reports.lastOrNull() shouldBe listOf(Bookmarks.Line(digit = 7, line = 10))
                view.state.doc.toString() shouldBe song
                set.defaultPrevented shouldBe true
            }
        }
    }

    "on a French layout the number keys type their digit with Shift, and set and go all the same" {
        val host = RecordingHost()

        withView(newState(host, basicSetup)) { view ->
            view.moveCursorTo(line = 10)
            // AZERTY: the key above Q types `&`, and `1` with Shift
            view.press(code = "Digit1", key = "1", shift = true)
            view.moveCursorTo(line = 30)
            view.press(code = "Digit1", key = "&")

            withClue("set, then gone to") {
                host.reports.lastOrNull() shouldBe listOf(Bookmarks.Line(digit = 1, line = 10))
                view.cursorLine() shouldBe 10
            }
        }
    }

    "setting a digit on its own line again removes it" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.pressSet(3)

            withClue("the toggle") {
                host.reports shouldBe listOf(listOf(Bookmarks.Line(digit = 3, line = 10)), emptyList())
            }
        }
    }

    "a digit on another line asks the host first: a no leaves it, a yes moves it" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 20)
            view.pressSet(3)

            withClue("the question") {
                host.questions shouldBe listOf(BookmarkOptions.Move(digit = 3, fromLine = 10, toLine = 20))
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
            }

            host.answer(yes = false)

            // Checked before the next press: after a wrong move that press would remove the bookmark, not ask
            withClue("a no") {
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
            }

            view.pressSet(3)
            host.answer(yes = true)

            val afterYes = view.state.bookmarkLines()

            withClue("a yes") {
                host.questions.size shouldBe 2
                afterYes shouldBe listOf(Bookmarks.Line(digit = 3, line = 20))
                host.reports.lastOrNull() shouldBe listOf(Bookmarks.Line(digit = 3, line = 20))
            }
        }
    }

    "asking takes the keyboard from the editor, and the answer gives it back" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.focus()
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 20)
            view.pressSet(3)

            val whileAsking = document.activeElement === view.contentDOM

            host.answer(yes = false)

            withClue("while the question is open, the editor does not have the keyboard") {
                whileAsking shouldBe false
            }

            withClue("after the answer, it has it again") {
                (document.activeElement === view.contentDOM) shouldBe true
            }
        }
    }

    "a yes after the song got shorter than the line moves nothing" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 40)
            view.pressSet(3)
            // The last ten lines go before the answer
            view.change(from = view.state.doc.line(30).to, to = view.state.doc.length)
            host.answer(yes = true)

            withClue("the song is 30 lines long now, and the bookmark stays") {
                view.state.doc.lines shouldBe 30
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
            }
        }
    }

    "a bookmark follows its line while text is typed above it, and each line it moves to is reported" {
        val host = RecordingHost()

        withView(newState(host)) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)

            view.change(from = 0, text = "a\nb\n")

            val afterAbove = host.reports.toList()

            view.change(from = view.state.doc.line(30).from, text = "c\n")

            val afterBelow = host.reports.toList()

            // Enter at the start of its line
            view.change(from = view.state.doc.line(12).from, text = "\n")

            withClue("two lines typed above") {
                afterAbove shouldBe listOf(
                    listOf(Bookmarks.Line(digit = 3, line = 10)),
                    listOf(Bookmarks.Line(digit = 3, line = 12)),
                )
            }

            withClue("a line typed below: nothing to report") {
                afterBelow shouldBe afterAbove
            }

            withClue("a line break typed at the start of its line takes it down with the text") {
                view.state.doc.line(13).text shouldBe "line 10"
                host.reports.lastOrNull() shouldBe listOf(Bookmarks.Line(digit = 3, line = 13))
            }
        }
    }

    "a blank line's bookmark stays on it while the line gets text and Enter makes new lines after it" {
        withView(newState(RecordingHost(), javascript(), doc = "a\n\nc\nd")) { view ->
            view.moveCursorTo(line = 2)
            view.pressSet(3)
            view.change(from = view.state.doc.line(2).from, text = "foo")
            view.moveCursorTo(line = 2, column = 3)
            insertNewlineAndIndent(view)
            view.change(from = view.state.selection.main.head, text = "bar")
            view.moveCursorTo(line = 3, column = 3)
            insertNewlineAndIndent(view)

            withClue("foo typed on it, then Enter twice") {
                view.state.doc.line(2).text shouldBe "foo"
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 2))
            }
        }
    }

    "Enter in the indentation of a bookmarked line takes the bookmark down with the text" {
        for (column in listOf(0, 4)) {
            withView(newState(RecordingHost(), javascript(), doc = indented)) { view ->
                view.moveCursorTo(line = 10)
                view.pressSet(3)
                view.moveCursorTo(line = 10, column = column)
                insertNewlineAndIndent(view)

                withClue("Enter at column $column") {
                    view.state.doc.line(11).text.trim() shouldBe "line 10"
                    view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 11))
                }
            }
        }
    }

    "a deletion from the line above into a bookmarked line's text takes the bookmark along with that text" {
        withView(newState(RecordingHost())) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 11)
            view.pressSet(4)
            // From the middle of line 9 into the text of line 10
            view.change(from = view.state.doc.line(9).from + 2, to = view.state.doc.line(10).from + 2)

            withClue("joined into line 9") {
                view.state.doc.line(9).text shouldBe "line 10"
                view.state.bookmarkLines() shouldBe listOf(
                    Bookmarks.Line(digit = 3, line = 9),
                    Bookmarks.Line(digit = 4, line = 10),
                )
            }
        }
    }

    "a deleted line's bookmark gives way to the bookmark of the line that takes its place" {
        withView(newState(RecordingHost())) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 11)
            view.pressSet(4)
            view.moveCursorTo(line = 10)
            // From the end of line 9 to the end of line 10
            deleteLine(view)

            withClue("CodeMirror's delete line") {
                view.state.doc.line(10).text shouldBe "line 11"
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 10))
            }
        }

        withView(newState(RecordingHost())) { view ->
            view.moveCursorTo(line = 10)
            view.pressSet(3)
            view.moveCursorTo(line = 11)
            view.pressSet(4)
            view.change(from = view.state.doc.line(10).from, to = view.state.doc.line(11).from)

            withClue("deleted from its start to the next line's") {
                view.state.doc.line(10).text shouldBe "line 11"
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 10))
            }
        }

        withView(newState(RecordingHost(), doc = "foo()\n\n// Section B\nbar()")) { view ->
            view.moveCursorTo(line = 3)
            view.pressSet(3)
            deleteLine(view)

            withClue("a blank line above: the line after the deleted one takes its place") {
                view.state.doc.line(3).text shouldBe "bar()"
                view.state.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 3))
            }
        }
    }

    "a paste ending in a line break over a whole indented line keeps its bookmark on the paste's line" {
        withView(newState(RecordingHost(), doc = "a();\n  b();\n  c();\nd();\ne();")) { view ->
            view.moveCursorTo(line = 3)
            view.pressSet(3)
            view.moveCursorTo(line = 4)
            view.pressSet(4)
            view.dispatch(
                view.state.update(
                    jsObject {
                        this.changes = jsObject<dynamic> {
                            this.from = view.state.doc.line(3).from
                            this.to = view.state.doc.line(4).from
                            this.insert = "p();\n"
                        }
                        this.userEvent = "input.paste"
                    }
                )
            )

            withClue("line 3 pasted over") {
                view.state.doc.line(3).text shouldBe "p();"
                view.state.bookmarkLines() shouldBe
                        listOf(Bookmarks.Line(digit = 3, line = 3), Bookmarks.Line(digit = 4, line = 4))
            }
        }
    }

    "a moved line takes its bookmark along, and so does the line it jumps over, through undo and redo" {
        withView(newState(RecordingHost(), history(jsObject<dynamic> {}))) { view ->
            view.moveCursorTo(line = 9)
            view.pressSet(3)
            view.moveCursorTo(line = 10)
            view.pressSet(4)
            moveLineUp(view)

            val afterUp = view.state.bookmarkLines()

            undo(view)

            val afterUndo = view.state.bookmarkLines()

            redo(view)

            withClue("line 10 moved up over line 9") {
                view.state.doc.line(9).text shouldBe "line 10"
                afterUp shouldBe listOf(Bookmarks.Line(digit = 3, line = 10), Bookmarks.Line(digit = 4, line = 9))
            }

            withClue("the undo") {
                afterUndo shouldBe listOf(Bookmarks.Line(digit = 3, line = 9), Bookmarks.Line(digit = 4, line = 10))
            }

            withClue("the redo") {
                view.state.bookmarkLines() shouldBe
                        listOf(Bookmarks.Line(digit = 3, line = 10), Bookmarks.Line(digit = 4, line = 9))
            }
        }

        withView(newState(RecordingHost())) { view ->
            view.moveCursorTo(line = 9)
            view.pressSet(3)
            view.moveCursorTo(line = 10)
            view.pressSet(4)
            view.moveCursorTo(line = 11)
            view.pressSet(5)
            view.moveCursorTo(line = 9)
            moveLineDown(view)

            withClue("line 9 moved down over line 10") {
                view.state.doc.line(10).text shouldBe "line 9"
                view.state.bookmarkLines() shouldBe listOf(
                    Bookmarks.Line(digit = 3, line = 10),
                    Bookmarks.Line(digit = 4, line = 9),
                    Bookmarks.Line(digit = 5, line = 11),
                )
            }
        }

        withView(newState(RecordingHost(), doc = "one\n\nthree\nfour")) { view ->
            view.pressSet(1)
            view.moveCursorTo(line = 3)
            view.pressSet(3)
            view.moveCursorTo(line = 2)
            moveLineDown(view)

            withClue("an empty line moved down") {
                view.state.doc.toString() shouldBe "one\nthree\n\nfour"
                view.state.bookmarkLines() shouldBe
                        listOf(Bookmarks.Line(digit = 1, line = 1), Bookmarks.Line(digit = 3, line = 2))
            }
        }

        withView(newState(RecordingHost(), basicSetup, doc = "a();\n  b();\n  c();\nd();\ne();")) { view ->
            view.moveCursorTo(line = 2)
            view.pressSet(2)
            view.moveCursorTo(line = 3)
            view.pressSet(3)
            // Two cursors, on lines 1 and 3: CodeMirror leaves the block on the first line where it is
            view.dispatch(
                view.state.update(
                    jsObject {
                        this.selection = EditorSelection.create(
                            arrayOf(EditorSelection.cursor(0), EditorSelection.cursor(view.state.doc.line(3).from))
                        )
                    }
                )
            )
            moveLineUp(view)

            withClue("two cursors moved up, one of them on the first line") {
                view.state.doc.line(2).text shouldBe "  c();"
                view.state.bookmarkLines() shouldBe
                        listOf(Bookmarks.Line(digit = 2, line = 3), Bookmarks.Line(digit = 3, line = 2))
            }
        }
    }

    "the undo and the redo of a reset keep the bookmarks on their lines" {
        val fourLines = listOf(5, 10, 20, 30).mapIndexed { index, line ->
            Bookmarks.Line(digit = index + 1, line = line)
        }

        withView(newState(RecordingHost(initial = fourLines), history(jsObject<dynamic> {}))) { view ->
            val original = (1..40).joinToString("\n") { "original $it" }

            view.dispatch(
                view.state.update(
                    jsObject {
                        this.changes = jsObject<dynamic> {
                            this.from = 0
                            this.to = view.state.doc.length
                            this.insert = original
                        }
                        this.effects = BookmarksExtension.keepLines.of(Unit)
                    }
                )
            )
            undo(view)

            val afterUndo = view.state.bookmarkLines()

            redo(view)

            withClue("the undo of a reset") {
                view.state.doc.line(1).text shouldBe "original 1"
                afterUndo shouldBe fourLines
            }

            withClue("its redo") {
                view.state.bookmarkLines() shouldBe fourLines
            }
        }
    }

    "a move line is an undo step of its own, so an edit right after it is undone alone and the redo still fits" {
        val ten = (1..10).joinToString("\n") { "line $it" }

        withView(newState(RecordingHost(), history(jsObject<dynamic> {}), doc = ten)) { view ->
            view.moveCursorTo(line = 5)
            view.pressSet(5)
            view.moveCursorTo(line = 8)
            view.pressSet(8)
            view.moveCursorTo(line = 5)
            moveLineDown(view)

            val afterMove = view.state.doc.toString()

            // Backspace at the start of the moved line, right after the move (the cursor moved with the line; moving it
            // again would end the undo step by itself)
            deleteCharBackward(view)
            undo(view)

            val afterFirstUndo = view.state.doc.toString()

            undo(view)

            val afterSecondUndo = view.state.bookmarkLines()
            val textAfterSecondUndo = view.state.doc.toString()

            redo(view)
            redo(view)

            withClue("the first undo takes back the Backspace only") {
                afterFirstUndo shouldBe afterMove
            }

            // Bookmark 5 went with the line its own was joined to, and the undo leaves it with that line's text
            withClue("the second undo takes back the move") {
                textAfterSecondUndo shouldBe ten
                afterSecondUndo shouldBe
                        listOf(Bookmarks.Line(digit = 5, line = 6), Bookmarks.Line(digit = 8, line = 8))
            }

            withClue("both redone") {
                view.state.doc.line(5).text shouldBe "line 6line 5"
                view.state.bookmarkLines() shouldBe
                        listOf(Bookmarks.Line(digit = 5, line = 5), Bookmarks.Line(digit = 8, line = 7))
            }
        }
    }

    "the gutter highlights a bookmarked line's number, with the digit after it" {
        // The higher digit higher up: the markers go to the gutter by position, not by digit. From the host, so a
        // failure throws here and not inside a key event, where the browser would only report it
        val host = RecordingHost(
            initial = listOf(Bookmarks.Line(digit = 4, line = 7), Bookmarks.Line(digit = 3, line = 20)),
        )

        withView(newState(host, lineNumbers())) { view ->
            val marked = view.dom
                .querySelectorAll(".cm-lineNumbers .cm-gutterElement.${BookmarksExtension.LINE_CLASS}")
                .asList()
                .filterIsInstance<Element>()
            // The last node of each marker, a text node if the digit came first
            val lastParts = marked.map { it.firstElementChild?.lastChild?.unsafeCast<Element>()?.className }

            withClue("the bookmarked lines' numbers, highlighted, each with its digit after it") {
                marked.map { it.textContent } shouldBe listOf("74", "203")
                lastParts shouldBe listOf(BookmarksExtension.DIGIT_CLASS, BookmarksExtension.DIGIT_CLASS)
            }
        }
    }

    "the editor starts from the host's bookmarks, drops a line past the end, and reports the drop" {
        val host = RecordingHost(
            initial = listOf(Bookmarks.Line(digit = 1, line = 5), Bookmarks.Line(digit = 2, line = 41)),
        )

        withView(newState(host)) { view ->
            val loaded = view.state.bookmarkLines()

            view.pressGo(1)

            withClue("loaded") {
                loaded shouldBe listOf(Bookmarks.Line(digit = 1, line = 5))
            }

            withClue("the go to the loaded one, and the report of the drop") {
                view.cursorLine() shouldBe 5
                host.reports shouldBe listOf(listOf(Bookmarks.Line(digit = 1, line = 5)))
            }
        }
    }

    "replacing the whole document keeps the bookmarks on their line numbers and drops one past the new end" {
        val host = RecordingHost(
            initial = listOf(Bookmarks.Line(digit = 3, line = 10), Bookmarks.Line(digit = 4, line = 35)),
        )
        val state = newState(host)
        val shorter = (1..30).joinToString("\n") { "new line $it" }

        val replaced = state.update(
            jsObject {
                this.changes = jsObject<dynamic> {
                    this.from = 0
                    this.to = state.doc.length
                    this.insert = shorter
                }
                this.effects = BookmarksExtension.keepLines.of(Unit)
            }
        ).state

        withClue("replaced by a song of 30 lines") {
            replaced.bookmarkLines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
        }
    }

    "a go scrolls its line to the middle of the editor" {
        val host = RecordingHost()
        val doc = (1..40).joinToString("\n") { "line $it" }

        withView(newState(host, doc = doc)) { view ->
            // A short editor, so line 30 of 40 lies far below the first screen
            view.dom.style.height = "200px"
            view.moveCursorTo(line = 30)
            view.pressSet(4)
            view.moveCursorTo(line = 1)
            view.scrollDOM.scrollTop = 0.0
            nextFrames()

            view.pressGo(4)
            // CodeMirror scrolls when it measures, on the next animation frame
            nextFrames()

            val block = view.lineBlockAt(view.state.selection.main.head)
            val lineMiddle = block.top + block.height / 2
            val editorMiddle = view.scrollDOM.scrollTop + view.scrollDOM.clientHeight / 2.0

            // The block's top counts from the document, a few pixels of padding below the scroller's top; one line of
            // tolerance covers that, a "nearest" scroll misses by several lines
            withClue("the bookmark's line ${block.top}..${block.bottom}, the editor's middle $editorMiddle") {
                abs(lineMiddle - editorMiddle) shouldBeLessThan block.height
            }
        }
    }
})

/** A host that records what the editor reports and asks, and answers the last question when told to. */
private class RecordingHost(initial: List<Bookmarks.Line> = emptyList()) {

    val reports = mutableListOf<List<Bookmarks.Line>>()
    val questions = mutableListOf<BookmarkOptions.Move>()
    private var pending: ((yes: Boolean) -> Unit)? = null

    val options = BookmarkOptions(
        initial = initial,
        onChanged = { reports.add(it) },
        askToMove = { move, answer ->
            questions.add(move)
            pending = answer
        },
    )

    /** Answers the last question. */
    fun answer(yes: Boolean) {
        val reply = pending ?: error("no question was asked")

        pending = null
        reply(yes)
    }
}

/** Waits [count] animation frames, so CodeMirror's measuring (and the scroll it applies) has run. */
private suspend fun nextFrames(count: Int = 3) {
    repeat(count) {
        suspendCoroutine { cont -> window.requestAnimationFrame { cont.resume(Unit) } }
    }
}
