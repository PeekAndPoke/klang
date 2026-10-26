/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.ui.comp.KlangNoticeModal
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.semanticui.semanticUI
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The page under test: a Play key of its own, and a button that shows a notice. */
private class NoticePage(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: NoticePage? = null
    }

    val log = mutableListOf<String>()

    init {
        current = this
    }

    fun tell() {
        modals.show<Boolean> { handle ->
            handle.onResult { closedByKey -> log.add(if (closedByKey == true) "key" else "away") }

            KlangNoticeModal(handle = handle, headline = "Leave fullscreen", okText = "Got it") { +"Press F11." }
        }
    }

    override fun VDom.render() {
        div {
            OnKey(KlangKeyBindings.PlayOrUpdate) { log.add("play") }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.NoticePage() = comp { NoticePage(it) }

/** A notice (KLANG-00350): one key, holding the keyboard, Esc closing it, the page's keys waiting meanwhile. */
class KlangNoticeModalSpec : StringSpec({

    suspend fun withPage(block: suspend (NoticePage) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { semanticUI(); keys(manager) }, view = { NoticePage() }) {
                block(NoticePage.current!!)
            }
        } finally {
            manager.dispose()
        }
    }

    fun press(key: String, ctrl: Boolean = false, shift: Boolean = false) {
        (document.activeElement ?: document.body!!).dispatchEvent(
            KeyboardEvent(
                "keydown",
                KeyboardEventInit(key = key, code = key, ctrlKey = ctrl, shiftKey = shift, bubbles = true, cancelable = true),
            )
        )
    }

    "the notice shows its text, and its one key has the keyboard, so Enter closes it" {
        withPage { page ->
            page.tell()

            eventually {
                document.querySelector("[role=dialog]")?.textContent shouldContain "Press F11."
                (document.activeElement as? HTMLElement)?.textContent shouldContain "Got it"
            }

            (document.activeElement as HTMLElement).click()

            eventually { page.log shouldContainExactly listOf("key") }
        }
    }

    "Esc closes it; while it is open the page's keys wait" {
        withPage { page ->
            page.tell()

            eventually { (document.querySelector("[role=dialog]") != null) shouldBe true }

            press(key = "Enter", ctrl = true, shift = true)
            press(key = "Escape")

            eventually { page.log shouldContainExactly listOf("key") }
        }
    }
})
