/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The mini-notation editors' undo and redo (KLANG-00325 step 3c): on the key map, while the tool is open. */
class MnEditorUndoKeysSpec : StringSpec({

    "Ctrl+Z undoes the tool's last edit and Ctrl+Y redoes it" {
        val manager = KeysManager(platform = KeyPlatform.Pc).apply { define(KlangKeyBindings) }
        val toolCtx = KlangUiToolContext(
            symbol = KlangSymbol(name = "n", variants = emptyList(), category = "test"),
            paramName = "pattern",
            currentValue = "\"a b\"",
            onCommit = {},
            onCancel = {},
        )

        fun press(key: String, code: String) = document.body!!.dispatchEvent(
            KeyboardEvent("keydown", KeyboardEventInit(key = key, code = code, ctrlKey = true, bubbles = true, cancelable = true))
        )

        fun shown() = document.getElementById(TestMnEditor.TEXT_ID)?.textContent

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { TestMnEditor(toolCtx) }) {
                eventually { shown() shouldBe "a b" }

                TestMnEditor.last!!.edit(newText = "c d")
                eventually { shown() shouldBe "c d" }

                press(key = "z", code = "KeyZ")
                eventually { shown() shouldBe "a b" }

                press(key = "y", code = "KeyY")
                eventually { shown() shouldBe "c d" }
            }
        } finally {
            manager.dispose()
        }
    }
})

/** The editors' shared base with nothing but its text, and an edit as a tool's button makes one. */
private class TestMnEditor(ctx: Ctx<Props>) : MnPatternEditorBase<TestMnEditor.Props>(ctx) {

    companion object {
        const val TEXT_ID = "mn-text"

        var last: TestMnEditor? = null
    }

    data class Props(override val toolCtx: KlangUiToolContext) : BaseProps

    init {
        last = this
    }

    fun edit(newText: String) {
        pushUndo()
        text = newText
    }

    override fun VDom.render() {
        div {
            id = TEXT_ID
            +text
        }
    }
}

@Suppress("FunctionName")
private fun Tag.TestMnEditor(toolCtx: KlangUiToolContext) = comp(TestMnEditor.Props(toolCtx = toolCtx)) { TestMnEditor(it) }
