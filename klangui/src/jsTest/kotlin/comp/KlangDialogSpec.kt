/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.selectCss
import io.peekandpoke.kraft.testing.textContent
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** The dialog (KLANG-00339): its title and text, the key row in the given order, the main key silver, its keys bound. */
class KlangDialogSpec : StringSpec({

    val styles = KlangControlStyles

    "a riveted dialog named by its title, its text, the keys in their order, the main one silver, no caps printed" {
        val log = mutableListOf<String>()
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(
                appSetup = { keys(manager) },
                view = {
                    KlangDialog(
                        title = "Move the bookmark here?",
                        actions = listOf(
                            KlangDialog.Action(label = "Keep", key = KlangKeyBindings.DialogClose, onClick = { log.add("keep") }),
                            KlangDialog.Action(
                                label = "Move",
                                key = KlangKeyBindings.DialogConfirm,
                                primary = true,
                                onClick = { log.add("move") },
                            ),
                        ),
                    ) {
                        +"It is on line 12 now."
                    }
                },
            ) { root ->
                // Named by its title, described by its text
                val dialog = root.selectCss("[role=dialog]").single()

                document.getElementById(dialog.getAttribute("aria-labelledby")!!)!!.textContent shouldBe "Move the bookmark here?"
                document.getElementById(dialog.getAttribute("aria-describedby")!!)!!.textContent shouldBe "It is on line 12 now."
                root.selectCss(".${styles.dialogBody}").textContent() shouldBe "It is on line 12 now."
                // The top pair of rivets only, none by the keys
                root.selectCss(".${styles.rivet}").size shouldBe 2
                root.selectCss(".${styles.rivetBottomLeft}, .${styles.rivetBottomRight}").size shouldBe 0
                root.selectCss(".${styles.keyCap}").size shouldBe 0

                val buttons = root.selectCss(".${styles.dialogKey} > button")

                buttons.map { it.textContent } shouldContainExactly listOf("Keep", "Move")
                buttons.map { it.classList.contains(styles.silverFlat.toString()) } shouldContainExactly listOf(false, true)

                (buttons[1] as HTMLElement).click()
                (buttons[0] as HTMLElement).click()

                log shouldContainExactly listOf("move", "keep")

                // The keys, through the key manager
                log.clear()

                fun press(key: String, ctrl: Boolean = false) {
                    val init = KeyboardEventInit(key = key, code = key, ctrlKey = ctrl, bubbles = true, cancelable = true)

                    document.body!!.dispatchEvent(KeyboardEvent("keydown", init))
                }

                press("Enter", ctrl = true)
                press("Escape")

                log shouldContainExactly listOf("move", "keep")
            }
        } finally {
            manager.dispose()
        }
    }
})
