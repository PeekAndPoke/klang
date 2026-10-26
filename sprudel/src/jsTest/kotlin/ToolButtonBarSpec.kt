/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.document
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The code tools' bar of buttons (KLANG-00325): Esc is the tool's Cancel, so it reverts what auto-update wrote. */
class ToolButtonBarSpec : StringSpec({

    "Esc answers with the tool's Cancel" {
        val manager = KeysManager(platform = KeyPlatform.Pc).apply { define(KlangKeyBindings) }
        val answered = mutableListOf<String>()

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    ToolButtonBar(
                        isInitialModified = true,
                        isCurrentModified = true,
                        onCancel = { answered.add("cancel") },
                        onReset = { answered.add("reset") },
                        onCommit = { answered.add("commit") },
                    )
                },
            ) {
                eventually {
                    document.querySelector(".ui.button") shouldNotBe null
                }

                document.body!!.dispatchEvent(
                    KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", code = "Escape", bubbles = true, cancelable = true))
                )

                answered shouldContainExactly listOf("cancel")
            }
        } finally {
            manager.dispose()
        }
    }
})
