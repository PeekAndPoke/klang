/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.ui.codemirror.KlangMinimap
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * The highlight buffer and the editor's minimap (KLANG-00410, KLANG-00411): the minimap follows the highlights'
 * strength, and the buffer's cancel puts the minimap's bands out with its own.
 */
class CodeMirrorHighlightBufferSpec : StringSpec({

    "the minimap takes the highlights' strength, given before or after it came" {
        val buffer = CodeMirrorHighlightBuffer()
        val minimap = KlangMinimap()

        buffer.strength = 0.4
        buffer.minimap = minimap
        withClue("set before the minimap came") { minimap.strength shouldBe 0.4 }

        buffer.strength = 0.7
        withClue("set after") { minimap.strength shouldBe 0.7 }
    }

    "a cancel puts the minimap's bands out, so does switching the highlights off, and a minimap taken away is put out" {
        val parent = document.createElement("div") as HTMLElement
        parent.style.height = "200px"
        document.body!!.appendChild(parent)

        val minimap = KlangMinimap()
        val view = EditorView(
            jsObject<EditorViewConfig> {
                this.state = EditorState.create(
                    jsObject<EditorStateConfig> {
                        this.doc = (1..20).joinToString("\n") { "note(\"c3\") // line $it" }
                        this.extensions = arrayOf(minimap.extension())
                    }
                )
                this.parent = parent
            }
        )

        try {
            val buffer = CodeMirrorHighlightBuffer()
            buffer.minimap = minimap

            minimap.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 2000.0)
            withClue("lit") { minimap.bands.isEmpty() shouldBe false }

            buffer.cancelAll()
            withClue("the buffer's cancel") { minimap.bands.isEmpty() shouldBe true }

            minimap.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 2000.0)
            buffer.enabled = false
            withClue("the highlights switched off") { minimap.bands.isEmpty() shouldBe true }

            buffer.enabled = true
            minimap.light(line = 3, fromColumn = 1, toColumn = 5, durationMs = 2000.0)
            buffer.minimap = null
            withClue("taken away") { minimap.bands.isEmpty() shouldBe true }
        } finally {
            view.destroy()
            parent.remove()
        }
    }
})
