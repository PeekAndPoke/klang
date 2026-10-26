/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.div
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/**
 * The DSL editor's keys (KLANG-00325 step 3b): go to definition and quick documentation at the cursor, wired as the
 * editor wires them, with a library name the docs know and no analysis (the registry lookup answers).
 */
class DslEditorKeysSpec : StringSpec({

    val note = KlangSymbol(name = "note", variants = emptyList(), category = "test", origin = KlangSymbol.Origin.Library("test"))

    class Host(
        hoverDocs: () -> Boolean = { true },
        onQuickDocumentation: ((KlangSymbol, String?) -> Unit)? = null,
    ) {
        val navigated = mutableListOf<String>()
        val popups = PopupsManager(PopupsManager.Settings(popupRenderer = PopupsManager.DefaultPopupFactory))
        val hoverPopup = HoverPopupCtrl(popups = popups, showDelayMs = 10, hideDelayMs = 10)

        val wiring = dslEditorExtension(
            docProvider = { name -> note.takeIf { it.name == name } },
            hoverPopup = hoverPopup,
            hoverContent = { symbol, _ -> div { +symbol.name } },
            popups = popups,
            onNavigate = { symbol, _ -> navigated.add(symbol.name) },
            hoverDocs = hoverDocs,
            onQuickDocumentation = onQuickDocumentation,
        )
    }

    suspend fun withEditor(host: Host, doc: String, block: suspend (EditorView) -> Unit) {
        val parent = document.createElement("div")
        document.body!!.appendChild(parent)

        val keymap = Keymap(platform = KeyPlatform.Pc, actions = KlangEditorKeyBindings.actions)
        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = doc
                        this.extensions = arrayOf<Any>(
                            *EditorKeyTable.setup(),
                            EditorKeyTable.extension(currentKeymap = { keymap }, commands = EditorKeyTable.commands + host.wiring.commands),
                            host.wiring.extension,
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

    fun EditorView.press(key: String, code: String, ctrl: Boolean = false, alt: Boolean = false) = KeyboardEvent(
        "keydown",
        KeyboardEventInit(key = key, code = code, ctrlKey = ctrl, altKey = alt, bubbles = true, cancelable = true),
    ).also { contentDOM.dispatchEvent(it) }

    /** The mouse moved onto the text at [pos], as a hover. */
    fun EditorView.hover(pos: Int) {
        val at = coordsAtPos(pos)!!

        contentDOM.dispatchEvent(
            MouseEvent(
                "mousemove",
                MouseEventInit(clientX = (at.left + 1).toInt(), clientY = ((at.top + at.bottom) / 2).toInt(), bubbles = true),
            )
        )
    }

    fun EditorView.cursorAt(pos: Int) {
        dispatch(state.update(jsObject { this.selection = jsObject<dynamic> { this.anchor = pos } }))
    }

    "the DSL extension brings the commands of go to definition and quick documentation" {
        Host().wiring.commands.keys shouldBe
            setOf(KlangEditorKeyBindings.GoToDefinition.id, KlangEditorKeyBindings.QuickDocumentation.id)
    }

    "Ctrl+B on a library name opens its docs, also with the cursor right after the name; elsewhere it only keeps the key" {
        val host = Host()

        withEditor(host = host, doc = "note(1) + 2") { view ->
            view.cursorAt(pos = 2)
            view.press(key = "b", code = "KeyB", ctrl = true)

            view.cursorAt(pos = 4)
            val onName = view.press(key = "b", code = "KeyB", ctrl = true)

            view.cursorAt(pos = 10)
            val elsewhere = view.press(key = "b", code = "KeyB", ctrl = true)

            host.navigated shouldContainExactly listOf("note", "note")
            onName.defaultPrevented shouldBe true
            elsewhere.defaultPrevented shouldBe true
        }
    }

    "Alt+Q shows the docs of the name at the cursor, and Esc closes them" {
        val host = Host()

        withEditor(host = host, doc = "note(1)") { view ->
            view.cursorAt(pos = 2)
            view.press(key = "q", code = "KeyQ", alt = true)
            delay(100)

            withClue("shown") { host.popups().size shouldBe 1 }

            view.press(key = "Escape", code = "Escape")
            delay(100)

            withClue("closed by Esc") { host.popups().size shouldBe 0 }
        }
    }

    "with a Help window of the host's, Alt+Q hands it the name at the cursor and opens no popup; elsewhere nothing" {
        val asked = mutableListOf<String>()
        val host = Host(onQuickDocumentation = { symbol, _ -> asked.add(symbol.name) })

        withEditor(host = host, doc = "note(1) + 2") { view ->
            view.cursorAt(pos = 2)
            view.press(key = "q", code = "KeyQ", alt = true)

            view.cursorAt(pos = 10)
            view.press(key = "q", code = "KeyQ", alt = true)
            delay(100)

            asked shouldContainExactly listOf("note")
            host.popups().size shouldBe 0
        }
    }

    "hovering a name shows its docs only while the hover help is on" {
        for ((on, popups) in listOf(true to 1, false to 0)) {
            val host = Host(hoverDocs = { on })

            withEditor(host = host, doc = "note(1)") { view ->
                view.hover(pos = 2)
                delay(100)

                withClue("hover help ${if (on) "on" else "off"}") { host.popups().size shouldBe popups }
            }
        }
    }

    "a docs popup opened by key closes on the next edit" {
        val host = Host()

        withEditor(host = host, doc = "note(1)") { view ->
            view.cursorAt(pos = 2)
            view.press(key = "q", code = "KeyQ", alt = true)
            delay(100)
            host.popups().size shouldBe 1

            view.dispatch(view.state.update(jsObject { this.changes = jsObject<dynamic> { this.from = 7; this.insert = ";" } }))
            delay(100)

            host.popups().size shouldBe 0
        }
    }

    "a docs popup opened by key closes when the cursor moves" {
        val host = Host()

        withEditor(host = host, doc = "note(1)") { view ->
            view.cursorAt(pos = 2)
            view.press(key = "q", code = "KeyQ", alt = true)
            delay(100)
            host.popups().size shouldBe 1

            view.cursorAt(pos = 6)
            delay(100)

            host.popups().size shouldBe 0
        }
    }
})
