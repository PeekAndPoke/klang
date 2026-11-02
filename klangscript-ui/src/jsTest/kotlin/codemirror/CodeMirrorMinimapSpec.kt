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
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit

/**
 * The editor's minimap ([klangMinimap]): a press on it starts its drag, never a text selection across the page (the
 * maintainer, 2026-11-02), so its primary-button mousedown is prevented; another button's is left alone.
 */
class CodeMirrorMinimapSpec : StringSpec({

    "a press on the minimap starts no text selection: its primary button's mousedown is prevented, another's is not" {
        val parent = document.createElement("div") as HTMLElement
        parent.style.height = "200px"
        document.body!!.appendChild(parent)

        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = (1..200).joinToString("\n") { "note(\"c3 e3\") // line $it" }
                        this.extensions = arrayOf(klangMinimap())
                    }
                )
                this.parent = parent
            }
        )

        fun press(button: Short): MouseEvent {
            val minimap = view.dom.querySelector(".cm-minimap-gutter") as HTMLElement

            return MouseEvent("mousedown", MouseEventInit(button = button, bubbles = true, cancelable = true))
                .also { minimap.dispatchEvent(it) }
        }

        try {
            withClue("the primary button: no selection starts") { press(button = 0).defaultPrevented shouldBe true }
            withClue("the right button: left alone, its menu may open") { press(button = 2).defaultPrevented shouldBe false }
        } finally {
            view.destroy()
            parent.remove()
        }
    }
})
