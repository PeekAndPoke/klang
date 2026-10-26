/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangKeyPriorities
import io.peekandpoke.klang.workshop.keys.KeyOverviewState
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap

/**
 * The editor's keys (KLANG-00325): sound with the page's on a PC and on a Mac, read from the extensions' own constants,
 * and its overview joins the bookmark keys into one row each.
 */
class KlangEditorKeyBindingsSpec : StringSpec({

    "the page's keys and the editor's together are sound on a PC and on a Mac" {
        KeyPlatform.entries.forEach { platform ->
            withClue(platform) {
                Keymap(platform = platform, actions = KlangKeyBindings.actions + KlangEditorKeyBindings.actions)
                    .problems.shouldBeEmpty()
            }
        }
    }

    "back to the last edit is the key the extension acts on" {
        KlangEditorKeyBindings.BackToLastEdit.keys shouldBe listOf(EditPlacesExtension.KEY)
    }

    "the overview: one row per bookmark group, Ctrl on a Mac too" {
        val keymap = Keymap(platform = KeyPlatform.Mac, actions = KlangEditorKeyBindings.actions)
        val entries = KeyOverviewState.of(title = null, rows = KlangEditorKeyBindings.overview, keymap = keymap).entries

        entries.first { it.title == "Go to bookmark" }.keys shouldBe listOf(listOf(listOf("⌃", "0…9")))
        entries.first { it.title == "Set bookmark" }.keys shouldBe listOf(listOf(listOf("⌃", "⇧", "0…9")))
    }

    "the overview ends with the page's key back into the editor: Focus editor, Esc" {
        val keymap = Keymap(platform = KeyPlatform.Pc, actions = KlangKeyBindings.actions + KlangEditorKeyBindings.actions)
        val entries = KeyOverviewState.of(title = null, rows = KlangEditorKeyBindings.overview, keymap = keymap).entries

        entries.last() shouldBe KeyOverviewState.Entry(title = "Focus editor", keys = listOf(listOf(listOf("Esc"))))
    }

    "the bookmark keys are the decided ones: Ctrl with the digits 0 to 9, Ctrl+Shift to set (KLANG-00323)" {
        KlangEditorKeyBindings.GoToBookmark.map { it.keys } shouldBe (0..9).map { listOf("Ctrl-$it") }
        KlangEditorKeyBindings.SetBookmark.map { it.keys } shouldBe (0..9).map { listOf("Ctrl-Shift-$it") }
    }

    "the jump key's action for a bookmark's digit, none for a digit no bookmark can have" {
        KlangEditorKeyBindings.goToBookmark(3)?.keys shouldBe listOf("Ctrl-3")
        KlangEditorKeyBindings.goToBookmark(0)?.keys shouldBe listOf("Ctrl-0")
        KlangEditorKeyBindings.goToBookmark(10) shouldBe null
    }

    "every editor action takes one of Klang's priorities, so they stay in one place" {
        KlangEditorKeyBindings.actions.filterNot { it.priority in KlangKeyPriorities.all }.map { it.id }.shouldBeEmpty()
    }
})
