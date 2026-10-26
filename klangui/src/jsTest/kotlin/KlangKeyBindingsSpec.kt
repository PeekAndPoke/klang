/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap
import io.peekandpoke.klang.workshop.keys.KeysManager
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/**
 * Klang's own keys (KLANG-00325) have no problem on either platform: no clash, no key the browser keeps, none that
 * cannot be read. The start-up check of the plan, run as a test.
 */
class KlangKeyBindingsSpec : StringSpec({

    "Klang's default keys are sound on a PC and on a Mac" {
        KeyPlatform.entries.forEach { platform ->
            withClue(platform) {
                Keymap(platform = platform, actions = KlangKeyBindings.actions).problems.shouldBeEmpty()
            }
        }
    }

    "the Esc chain on the song page: an open panel's close answers first, then Esc focuses the editor" {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val keys = KeysManager(platform = KeyPlatform.Pc, target = host).apply { define(KlangKeyBindings) }
        val answered = mutableListOf<String>()
        var panelOpen = true

        // Both in the page's layer: the order of the actions in the bag decides, close before focus
        keys.handle(KlangKeyBindings.FocusEditor) { answered.add("focus") }
        keys.handle(KlangKeyBindings.PanelClose, enabled = { panelOpen }) {
            answered.add("close")
            panelOpen = false
        }

        fun esc() = host.dispatchEvent(
            KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
        )

        try {
            esc()
            esc()
            answered shouldContainExactly listOf("close", "focus")
        } finally {
            keys.dispose()
            host.remove()
        }
    }
})
