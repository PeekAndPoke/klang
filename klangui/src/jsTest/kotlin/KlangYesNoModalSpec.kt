/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
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
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The page under test: a Play key of its own, and a button that asks the yes or no question. */
private class QuestionPage(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: QuestionPage? = null
    }

    val log = mutableListOf<String>()

    init {
        current = this
    }

    fun ask() {
        modals.show<Boolean> { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = "Move it?",
                content = { +"Really?" },
                yesText = "Move",
                noText = "Keep",
                onResult = { yes -> log.add(if (yes) "yes" else "no") },
            )
        }
    }

    override fun VDom.render() {
        div {
            OnKey(KlangKeyBindings.PlayOrUpdate) { log.add("play") }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.QuestionPage() = comp { QuestionPage(it) }

private fun key(key: String, code: String, ctrl: Boolean = false, shift: Boolean = false) = KeyboardEvent(
    "keydown",
    KeyboardEventInit(key = key, code = code, ctrlKey = ctrl, shiftKey = shift, bubbles = true, cancelable = true),
)

/**
 * The yes or no question on the key manager (KLANG-00325): Ctrl+Enter answers yes, Esc closes as a no, and while it is
 * open the page's keys wait, as the mouse does under its dimmer.
 */
class KlangYesNoModalSpec : StringSpec({

    suspend fun withPage(block: suspend (QuestionPage) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { semanticUI(); keys(manager) }, view = { QuestionPage() }) {
                block(QuestionPage.current!!)
            }
        } finally {
            manager.dispose()
        }
    }

    fun press(event: KeyboardEvent) {
        (document.activeElement ?: document.body!!).dispatchEvent(event)
    }

    "Ctrl+Enter answers yes; the page's Play waits while the question is open" {
        withPage { page ->
            page.ask()

            eventually {
                (document.querySelector("[role=dialog]") != null) shouldBe true
            }

            press(key(key = "Enter", code = "Enter", ctrl = true, shift = true))
            press(key(key = "Enter", code = "Enter", ctrl = true))

            eventually {
                page.log shouldContainExactly listOf("yes")
            }

            withClue("the question gone, Play answers again") {
                press(key(key = "Enter", code = "Enter", ctrl = true, shift = true))
                page.log shouldContainExactly listOf("yes", "play")
            }
        }
    }

    "Esc closes the question as a no" {
        withPage { page ->
            page.ask()

            eventually {
                (document.querySelector("[role=dialog]") != null) shouldBe true
            }

            press(key(key = "Escape", code = "Escape"))

            eventually {
                page.log shouldContainExactly listOf("no")
            }
        }
    }
})
