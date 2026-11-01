/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.codetools.CodeToolModal
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.semanticui.semanticUI
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** A page with the search over everything on Ctrl+K (as the workbench has it), and a button that opens a code tool. */
private class ToolPage(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: ToolPage? = null
    }

    val log = mutableListOf<String>()

    var handle: ModalsManager.Handle<Unit>? = null

    init {
        current = this
    }

    fun openTool() {
        modals.show<Unit> { h ->
            handle = h
            CodeToolModal(handle = h) { div(classes = "the-tool") { +"TOOL" } }
        }
    }

    override fun VDom.render() {
        div {
            OnKey(KlangKeyBindings.SearchEverywhere) { log.add("search") }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.ToolPage() = comp { ToolPage(it) }

/** A code tool in its modal (KLANG-00403): while it is open the page's keys wait, so the Motor's menu cannot open behind it. */
class CodeToolModalSpec : StringSpec({

    fun ctrlK() {
        (document.activeElement ?: document.body!!).dispatchEvent(
            KeyboardEvent("keydown", KeyboardEventInit(key = "k", code = "KeyK", ctrlKey = true, bubbles = true, cancelable = true))
        )
    }

    "the page's Ctrl+K waits while a code tool is open, and answers again when it is closed" {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { semanticUI(); keys(manager) }, view = { ToolPage() }) {
                val page = ToolPage.current!!

                page.openTool()

                eventually { (document.querySelector(".the-tool") != null) shouldBe true }

                ctrlK()

                page.log shouldContainExactly emptyList()

                page.handle!!.close()

                eventually { (document.querySelector(".the-tool") == null) shouldBe true }

                ctrlK()

                eventually { page.log shouldContainExactly listOf("search") }
            }
        } finally {
            manager.dispose()
        }
    }
})
