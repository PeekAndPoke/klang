/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.cursorLineDown
import io.peekandpoke.klang.codemirror.ext.javascript
import io.peekandpoke.klang.codemirror.ext.moveLineDown
import io.peekandpoke.klang.codemirror.ext.toggleComment
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * The wiring of the edit places into CodeMirror (KLANG-00322), on a real editor state and view.
 *
 * [EditPlacesSpec] holds the rule. This spec holds what the rule cannot see: that every change counts, with a user
 * event or without (real editor commands among them), that a selection move does not, that each changed range reaches
 * the rule, that replacing the whole document empties the list, that the key reaches the step back and never the
 * browser, and that the step back scrolls its place to the editor's middle.
 */
class EditPlacesExtensionSpec : StringSpec({

    val song = (1..40).joinToString("\n") { "line $it" }

    /** A state with the edit places and [extra] extensions (a language, for the comment command). */
    fun newState(vararg extra: Any): EditorState = EditorState.create(
        jsObject<EditorStateConfig> {
            this.doc = song
            this.extensions = arrayOf<Any>(EditPlacesExtension.extension, *extra).unsafeCast<Array<Extension>>()
        }
    )

    fun EditorState.placeLines(): List<Int> =
        field(EditPlacesExtension.field).unsafeCast<EditPlaces>().places.map { doc.lineAt(it).number }

    /**
     * Inserts [text] at the end of each of [lines] in one change, with [userEvent] or with none (as a tool's commit and
     * some commands send it); the cursor ends after the last insert.
     */
    fun EditorState.insertAtEndOfEach(lines: List<Int>, text: String, userEvent: String? = null): EditorState {
        val ends = lines.sorted().map { doc.line(it).to }

        return update(
            jsObject {
                this.changes = ends.map { at ->
                    jsObject<dynamic> {
                        this.from = at
                        this.insert = text
                    }
                }.toTypedArray()
                this.selection = jsObject<dynamic> { this.anchor = ends.last() + ends.size * text.length }

                if (userEvent != null) {
                    this.userEvent = userEvent
                }
            }
        ).state
    }

    fun EditorState.insertAtEndOf(line: Int, text: String, userEvent: String? = null): EditorState =
        insertAtEndOfEach(lines = listOf(line), text = text, userEvent = userEvent)

    fun EditorView.moveCursorTo(line: Int) {
        val at = state.doc.line(line).from

        dispatch(state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = at } }))
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

    "every change makes a place, with a user event or without; a selection move makes none" {
        val typed = newState().insertAtEndOf(line = 10, text = "a", userEvent = "input.type")
        // No user event, as an editor tool commits
        val unmarked = typed.insertAtEndOf(line = 30, text = "b")
        // A selection move with no change, as "go to definition" dispatches it (KLANG-00321)
        val moved = unmarked.update(
            jsObject {
                this.selection = jsObject<dynamic> { this.anchor = 0 }
                this.scrollIntoView = true
            }
        ).state

        withClue("a change with a user event") {
            typed.placeLines() shouldBe listOf(10)
        }

        withClue("a change without a user event") {
            unmarked.placeLines() shouldBe listOf(10, 30)
        }

        withClue("a selection move") {
            moved.placeLines() shouldBe listOf(10, 30)
        }
    }

    "an editor command makes a place: toggle comment and move line down" {
        withView(newState(javascript())) { view ->
            // Toggle comment dispatches with no user event at all
            view.moveCursorTo(line = 30)
            toggleComment(view)

            val commented = view.state.doc.line(30).text
            val afterComment = view.state.placeLines()

            // Move line down sends `move.line`
            view.moveCursorTo(line = 10)
            moveLineDown(view)

            val moved = view.state.doc.line(11).text
            val afterMove = view.state.placeLines()

            withClue("toggle comment") {
                commented shouldBe "// line 30"
                afterComment shouldBe listOf(30)
            }

            withClue("move line down") {
                moved shouldBe "line 10"
                afterMove shouldBe listOf(30, 11)
            }
        }
    }

    "an edit at several places hands every range to the rule" {
        val state = newState()
            .insertAtEndOf(line = 20, text = "a", userEvent = "input.type")
            .insertAtEndOfEach(lines = listOf(3, 40), text = "b", userEvent = "input.type")

        withClue("several places around the newest place") {
            state.placeLines() shouldBe listOf(20, 40)
        }
    }

    "an edit at several places merges by its first range too" {
        val state = newState()
            .insertAtEndOf(line = 20, text = "a", userEvent = "input.type")
            .insertAtEndOfEach(lines = listOf(19, 40), text = "b", userEvent = "input.type")

        withClue("several places, the first one near the newest place") {
            state.placeLines() shouldBe listOf(40)
        }
    }

    "replacing the whole document empties the list" {
        val typed = newState().insertAtEndOf(line = 10, text = "a", userEvent = "input.type")
        val replaced = typed.update(
            jsObject {
                this.changes = jsObject<dynamic> {
                    this.from = 0
                    this.to = typed.doc.length
                    this.insert = song
                }
                this.effects = EditPlacesExtension.clear.of(Unit)
            }
        ).state

        withClue("replacing the whole document") {
            replaced.placeLines() shouldBe emptyList()
        }
    }

    "a cursor move by key or by mouse ends the walk; the step back's own move does not" {
        val state = newState()
            .insertAtEndOf(line = 5, text = "a", userEvent = "input.type")
            .insertAtEndOf(line = 20, text = "b", userEvent = "input.type")
            .insertAtEndOf(line = 35, text = "c", userEvent = "input.type")

        withView(state) { view ->
            fun back(): Int {
                EditPlacesExtension.goBack(view)

                return view.state.doc.lineAt(view.state.selection.main.head).number
            }

            fun walkIndex(): Int? = view.state.field(EditPlacesExtension.field).unsafeCast<EditPlaces>().walkIndex

            view.moveCursorTo(line = 12)

            val first = back()
            val second = back()
            val walkAfterSteps = walkIndex()

            // By key: one line down, from line 20 to 21, with the real command
            cursorLineDown(view)

            val afterKey = back()

            back()
            // By mouse: a selection with the pointer's user event, as CodeMirror dispatches a click
            view.dispatch(
                view.state.update(
                    jsObject {
                        this.selection = jsObject<dynamic> { this.anchor = view.state.doc.line(12).from }
                        this.userEvent = "select.pointer"
                    }
                )
            )

            val walkAfterClick = walkIndex()
            val afterClick = back()

            withClue("the step back's own move keeps the walk") {
                first shouldBe 35
                second shouldBe 20
                walkAfterSteps shouldBe 1
            }

            withClue("a cursor move by key starts over from the newest place") {
                afterKey shouldBe 35
            }

            withClue("a cursor move by mouse starts over from the newest place") {
                walkAfterClick shouldBe null
                afterClick shouldBe 35
            }
        }
    }

    "the key steps back and is never left to the browser" {
        val state = newState()
            .insertAtEndOf(line = 5, text = "a", userEvent = "input.type")
            .insertAtEndOf(line = 30, text = "b", userEvent = "input.type")

        // CodeMirror's `Mod` is Cmd on a Mac and Ctrl elsewhere, by the same platform test
        val mac = window.navigator.platform.contains("Mac")

        withView(state) { view ->
            fun pressKey(): KeyboardEvent {
                val event = KeyboardEvent(
                    "keydown",
                    jsObject<KeyboardEventInit> {
                        this.key = "Backspace"
                        this.ctrlKey = !mac
                        this.metaKey = mac
                        this.shiftKey = true
                        this.bubbles = true
                        this.cancelable = true
                    }
                )

                view.contentDOM.dispatchEvent(event)

                return event
            }

            val first = pressKey()
            val lineAfterFirst = view.state.doc.lineAt(view.state.selection.main.head).number
            val second = pressKey()
            val lineAfterSecond = view.state.doc.lineAt(view.state.selection.main.head).number

            withClue("the key steps back") {
                lineAfterFirst shouldBe 5
                first.defaultPrevented shouldBe true
                // No older place: the cursor stays, and the key still does not reach the browser
                lineAfterSecond shouldBe 5
                second.defaultPrevented shouldBe true
            }
        }
    }

    "the step back scrolls its place to the middle of the editor" {
        val state = newState().insertAtEndOf(line = 30, text = "a", userEvent = "input.type")

        withView(state) { view ->
            // A short editor, so line 30 of 40 lies far below the first screen
            view.dom.style.height = "200px"
            view.moveCursorTo(line = 1)
            view.scrollDOM.scrollTop = 0.0
            nextFrames()

            EditPlacesExtension.goBack(view)
            // CodeMirror scrolls when it measures, on the next animation frame
            nextFrames()

            val block = view.lineBlockAt(view.state.selection.main.head)
            val placeMiddle = block.top + block.height / 2
            val editorMiddle = view.scrollDOM.scrollTop + view.scrollDOM.clientHeight / 2.0

            // The block's top counts from the document, a few pixels of content padding below the scroller's top;
            // the tolerance of one line covers that, the old "nearest" scroll misses by about 85 px
            withClue("the place's line ${block.top}..${block.bottom}, the editor's middle $editorMiddle") {
                abs(placeMiddle - editorMiddle) shouldBeLessThan block.height
            }
        }
    }
})

/** Waits [count] animation frames, so CodeMirror's measuring (and the scroll it applies) has run. */
private suspend fun nextFrames(count: Int = 3) {
    repeat(count) {
        suspendCoroutine { cont -> window.requestAnimationFrame { cont.resume(Unit) } }
    }
}
