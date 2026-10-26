/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.Completion
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.autocompletion
import io.peekandpoke.klang.codemirror.ext.completeFromList
import io.peekandpoke.klang.codemirror.ext.completionStatus
import io.peekandpoke.klang.codemirror.ext.javascript
import io.peekandpoke.klang.codemirror.ext.openSearchPanel
import io.peekandpoke.klang.codemirror.ext.searchPanelOpen
import io.peekandpoke.klang.codemirror.ext.selectedCompletion
import io.peekandpoke.klang.codemirror.ext.startCompletion
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/**
 * The editor on the key map (KLANG-00325 step 3a): every action of the table has a command that exists in the
 * installed CodeMirror packages; keys act through the table, the user's own included; CodeMirror's own keys that the
 * table does not name are gone; and of the actions on one key, the higher priority goes first, the next one only when
 * the first did nothing.
 */
class EditorKeyTableSpec : StringSpec({

    val pc = KeyPlatform.Pc

    /** Runs [block] on an editor with the editor's setup and keys, read from [keymap], on [doc], and [extra] after them. */
    suspend fun withEditor(
        doc: String,
        keymap: Keymap = Keymap(platform = pc, actions = KlangEditorKeyBindings.actions),
        extra: List<Extension> = emptyList(),
        block: suspend (EditorView) -> Unit,
    ) {
        val parent = document.createElement("div")
        document.body!!.appendChild(parent)

        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = doc
                        this.extensions = arrayOf<Any>(
                            *EditorKeyTable.setup(),
                            EditorKeyTable.extension(currentKeymap = { keymap }, commands = EditorKeyTable.commands),
                            *extra.toTypedArray(),
                        ).unsafeCast<Array<Extension>>()
                    }
                )
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

    fun keyDown(key: String, code: String, ctrl: Boolean, alt: Boolean, shift: Boolean) = KeyboardEvent(
        "keydown",
        KeyboardEventInit(key = key, code = code, ctrlKey = ctrl, altKey = alt, shiftKey = shift, bubbles = true, cancelable = true),
    )

    fun EditorView.press(key: String, code: String = "", ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        keyDown(key = key, code = code, ctrl = ctrl, alt = alt, shift = shift).also { contentDOM.dispatchEvent(it) }

    /** Presses a key in the search panel's search field, as a user typing there does. */
    fun EditorView.pressInSearchField(key: String, code: String = key, shift: Boolean = false) =
        keyDown(key = key, code = code, ctrl = false, alt = false, shift = shift)
            .also { dom.querySelector(".cm-search input[name=search]")!!.dispatchEvent(it) }

    /** Waits until CodeMirror would let a key act on the open completion list (its `interactionDelay`, 75 ms). */
    suspend fun EditorView.awaitCompletionList() {
        repeat(50) {
            if (completionStatus(state) == "active") {
                delay(150)

                return
            }

            delay(20)
        }
    }

    fun EditorView.cursorAt(line: Int) {
        dispatch(state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = state.doc.line(line).from } }))
    }

    "every action of the table has a command, and every command exists in the installed packages" {
        val bookmarks = BookmarksExtension.commands(BookmarkOptions(initial = emptyList(), onChanged = {}, askToMove = { _, _ -> }))
        val all = EditorKeyTable.commands + EditPlacesExtension.commands + bookmarks

        withClue("actions without a command: only those the DSL extension brings, which knows the names") {
            KlangEditorKeyBindings.actions.map { it.id }.filterNot { it in all } shouldContainExactly
                listOf(KlangEditorKeyBindings.GoToDefinition.id, KlangEditorKeyBindings.QuickDocumentation.id)
        }

        withClue("commands that are not functions: an import from the wrong package is undefined") {
            all.filterValues { commands -> commands.any { jsTypeOf(it) != "function" } }.keys.shouldBeEmpty()
        }
    }

    "a key of the table acts and is prevented; the editor's text keys still type" {
        withEditor(doc = "one\ntwo") { view ->
            view.cursorAt(line = 1)
            val duplicate = view.press(key = "d", code = "KeyD", ctrl = true)

            withClue("Ctrl+D duplicates the line") {
                view.state.doc.toString() shouldBe "one\none\ntwo"
                duplicate.defaultPrevented shouldBe true
            }

            view.cursorAt(line = 3)
            view.press(key = "ArrowUp", code = "ArrowUp", ctrl = true, shift = true)

            withClue("Ctrl+Shift+Up moves the line up") { view.state.doc.toString() shouldBe "one\ntwo\none" }
        }
    }

    "a key the user set acts, and the default it replaced passes on" {
        val keymap = Keymap(
            platform = pc,
            actions = KlangEditorKeyBindings.actions,
            userKeys = mapOf(KlangEditorKeyBindings.DuplicateLine.id to listOf("Alt-D")),
        )

        withEditor(doc = "one", keymap = keymap) { view ->
            val default = view.press(key = "d", code = "KeyD", ctrl = true)
            val users = view.press(key = "d", code = "KeyD", alt = true)

            view.state.doc.toString() shouldBe "one\none"
            default.defaultPrevented shouldBe false
            users.defaultPrevented shouldBe true
        }
    }

    "CodeMirror's own keys the table does not name are gone: Alt+Up, Ctrl+Shift+K, Ctrl+]" {
        withEditor(doc = "one\ntwo") { view ->
            view.cursorAt(line = 2)
            val pressed = listOf(
                view.press(key = "ArrowUp", code = "ArrowUp", alt = true),
                view.press(key = "K", code = "KeyK", ctrl = true, shift = true),
                view.press(key = "]", code = "BracketRight", ctrl = true),
            )

            view.state.doc.toString() shouldBe "one\ntwo"
            pressed.map { it.defaultPrevented } shouldContainExactly listOf(false, false, false)
        }
    }

    "one key, several actions: the higher priority first, the next only when the first did nothing" {
        val ran = mutableListOf<String>()

        fun recording(name: String, acts: Boolean): Command = {
            ran.add(name)
            acts
        }

        fun escWith(closeActs: Boolean): Pair<List<String>, Boolean> {
            ran.clear()
            val commands = mapOf(
                KlangEditorKeyBindings.BackToOneCursor.id to listOf(recording(name = "one cursor", acts = false)),
                KlangEditorKeyBindings.CloseCompletion.id to listOf(recording(name = "close", acts = closeActs)),
            )
            val esc = KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape"))
            val keymap = Keymap(platform = pc, actions = KlangEditorKeyBindings.actions)
            val acted = EditorKeyTable.run(view = js("({})").unsafeCast<EditorView>(), event = esc, keymap = keymap, commands = commands)

            return ran.toList() to acted
        }

        withClue("the list closes: Esc does nothing else") { escWith(closeActs = true) shouldBe (listOf("close") to true) }

        withClue("no list: the editor's own Esc, and nothing acted, so the key goes on") {
            escWith(closeActs = false) shouldBe (listOf("close", "one cursor") to false)
        }
    }

    "the search panel's own keys: F3 finds the next match and never reaches the browser, Esc closes the panel" {
        withEditor(doc = "a b a b a") { view ->
            openSearchPanel(view)
            val field = view.dom.querySelector(".cm-search input[name=search]").unsafeCast<HTMLInputElement>()
            field.value = "a"
            field.dispatchEvent(Event("change", EventInit(bubbles = true)))

            val next = view.pressInSearchField(key = "F3")

            withClue("F3 in the field: a match selected, the key kept from the browser") {
                view.state.selection.main.to shouldBe 1
                next.defaultPrevented shouldBe true
            }

            field.value = "zzz"
            field.dispatchEvent(Event("change", EventInit(bubbles = true)))
            val noMatch = view.pressInSearchField(key = "F3")

            withClue("F3 without a match: still kept from the browser, whose find bar would open") {
                noMatch.defaultPrevented shouldBe true
            }

            val esc = view.pressInSearchField(key = "Escape")

            withClue("Esc in the field: the panel closed, the key kept from the page") {
                searchPanelOpen(view.state) shouldBe false
                esc.defaultPrevented shouldBe true
            }
        }
    }

    "the completion list's keys: arrows move in it, Enter accepts, Esc closes it, and each passes on while no list is open" {
        val options = arrayOf("alpha", "beta").map { name -> jsObject<Completion> { this.label = name } }.toTypedArray()
        val source = autocompletion(jsObject { this.override = arrayOf(completeFromList(options)) })

        withEditor(doc = "one", extra = listOf(source)) { view ->
            // Mid-line: the table's new line goes below the line, CodeMirror's own Enter would break it ("o", "ne")
            view.dispatch(view.state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = 1 } }))

            withClue("Shift+Enter without a list starts a new line below") {
                view.press(key = "Enter", code = "Enter", shift = true)
                view.state.doc.toString() shouldBe "one\n"
            }

            startCompletion(view)
            view.awaitCompletionList()
            view.press(key = "ArrowDown", code = "ArrowDown")

            withClue("ArrowDown moves in the list") { selectedCompletion(view.state)?.label shouldBe "beta" }

            val enter = view.press(key = "Enter", code = "Enter")

            withClue("Enter accepts, no line break") {
                view.state.doc.toString() shouldBe "one\nbeta"
                enter.defaultPrevented shouldBe true
            }

            startCompletion(view)
            view.awaitCompletionList()
            val closing = view.press(key = "Escape", code = "Escape")
            val after = view.press(key = "Escape", code = "Escape")

            withClue("Esc closes the list; the next Esc, with nothing left to do, goes on to the page") {
                completionStatus(view.state) shouldBe null
                closing.defaultPrevented shouldBe true
                after.defaultPrevented shouldBe false
            }
        }
    }

    "the editor reads the app's key map when it knows the editor's keys, else its own presets" {
        val own = Keymap(platform = pc, actions = KlangEditorKeyBindings.actions)
        val withEditorKeys = Keymap(platform = pc, actions = KlangEditorKeyBindings.actions, userKeys = mapOf("x" to listOf("F9")))
        val withoutEditorKeys = Keymap(platform = pc, actions = emptyList())

        EditorKeyTable.keymapOf(app = null, own = own) shouldBe own
        EditorKeyTable.keymapOf(app = withoutEditorKeys, own = own) shouldBe own
        EditorKeyTable.keymapOf(app = withEditorKeys, own = own) shouldBe withEditorKeys
    }

    "block comment: an empty one with the cursor inside where nothing is selected, else the selection wrapped" {
        withEditor(doc = "abc", extra = listOf(javascript().unsafeCast<Extension>())) { view ->
            val empty = view.press(key = "?", code = "Slash", ctrl = true, shift = true)

            withClue("nothing selected") {
                view.state.doc.toString() shouldBe "/**/abc"
                view.state.selection.main.head shouldBe 2
                empty.defaultPrevented shouldBe true
            }

            view.dispatch(view.state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = 4; this.head = 7 } }))
            view.press(key = "?", code = "Slash", ctrl = true, shift = true)

            withClue("abc selected: wrapped") { view.state.doc.toString() shouldBe "/**//* abc */" }
        }
    }

    "unselect last occurrence takes back the newest of select next occurrence, also after the search wrapped round" {
        withEditor(doc = "a a a") { view ->
            repeat(3) { view.press(key = "j", code = "KeyJ", alt = true) }
            view.state.selection.ranges.size shouldBe 3

            val unselect = view.press(key = "J", code = "KeyJ", alt = true, shift = true)

            withClue("from the first: the last one goes") {
                view.state.selection.ranges.map { it.from } shouldContainExactly listOf(0, 2)
                view.state.selection.main.from shouldBe 0
                unselect.defaultPrevented shouldBe true
            }

            // From the middle one: the third, then round to the first, which is the newest
            view.dispatch(view.state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = 2 } }))
            repeat(3) { view.press(key = "j", code = "KeyJ", alt = true) }
            view.press(key = "J", code = "KeyJ", alt = true, shift = true)

            withClue("from the middle: the first one goes, the main one stays") {
                view.state.selection.ranges.map { it.from } shouldContainExactly listOf(2, 4)
                view.state.selection.main.from shouldBe 2
            }
        }
    }

    "replace opens the search panel with the cursor in its replace field, and keeps Ctrl+R from the browser" {
        withEditor(doc = "a b") { view ->
            val replace = view.press(key = "r", code = "KeyR", ctrl = true)

            searchPanelOpen(view.state) shouldBe true
            document.activeElement shouldBe view.dom.querySelector(".cm-search input[name=replace]")
            replace.defaultPrevented shouldBe true
        }
    }

    "reformat with nothing selected reindents the whole document, the cursor staying where it is" {
        withEditor(doc = "{\nx\n}", extra = listOf(javascript().unsafeCast<Extension>())) { view ->
            view.press(key = "l", code = "KeyL", ctrl = true, alt = true)

            view.state.doc.toString() shouldBe "{\n  x\n}"
            view.state.selection.main.head shouldBe 0
        }
    }

    "Alt+Shift+click adds a cursor, Ctrl+click does not" {
        withEditor(doc = "one two three") { view ->
            fun clickAt(pos: Int, alt: Boolean = false, shift: Boolean = false, ctrl: Boolean = false) {
                val at = view.coordsAtPos(pos)!!
                val init = MouseEventInit(
                    clientX = at.left.toInt() + 1,
                    clientY = ((at.top + at.bottom) / 2).toInt(),
                    altKey = alt,
                    shiftKey = shift,
                    ctrlKey = ctrl,
                    bubbles = true,
                    cancelable = true,
                    button = 0,
                    detail = 1,
                )
                view.contentDOM.dispatchEvent(MouseEvent("mousedown", init))
                document.dispatchEvent(MouseEvent("mouseup", init))
            }

            clickAt(pos = 8, ctrl = true)
            withClue("Ctrl+click: one cursor") { view.state.selection.ranges.size shouldBe 1 }

            clickAt(pos = 4, alt = true, shift = true)
            withClue("Alt+Shift+click: a second cursor") {
                view.state.selection.ranges.map { it.head }.sorted() shouldContainExactly listOf(4, 8)
            }
        }
    }
})
