/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.basicSetup
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/**
 * CodeMirror's side of the key manager's text-field rule (KLANG-00325): an Esc the editor uses (here: a selection to
 * simplify; closing its completion works the same way) is prevented, one it ignores is not, so the manager answers
 * only the second. A CodeMirror upgrade that stopped preventing would make one Esc act twice, in the editor and on a
 * panel; this spec says so first.
 */
class CodeMirrorEscSpec : StringSpec({

    "CodeMirror prevents an Esc it uses (a selection to simplify) and leaves one it ignores (a plain cursor)" {
        val parent = document.createElement("div")
        document.body!!.appendChild(parent)

        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = "note(\"c3 e3\")"
                        this.extensions = arrayOf<Any>(basicSetup).unsafeCast<Array<Extension>>()
                    }
                )
                this.parent = parent
            }
        )

        fun esc(): KeyboardEvent =
            KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
                .also { view.contentDOM.dispatchEvent(it) }

        try {
            view.dispatch(
                view.state.update(
                    jsObject { this.selection = jsObject<dynamic> { this.anchor = 0; this.head = 4 } }
                )
            )

            withClue("a selection to simplify: the editor uses the Esc") { esc().defaultPrevented shouldBe true }
            withClue("a plain cursor: nothing to do, the Esc goes on") { esc().defaultPrevented shouldBe false }
        } finally {
            view.destroy()
            parent.remove()
        }
    }
})
