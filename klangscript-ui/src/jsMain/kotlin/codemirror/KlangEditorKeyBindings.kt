/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.workshop.keys.KeyAction
import io.peekandpoke.klang.workshop.keys.KeyActionGroup
import io.peekandpoke.klang.workshop.keys.KeyBindings
import io.peekandpoke.klang.workshop.keys.KeyOverviewRow

/**
 * The code editor's keys (KLANG-00325), in the scope `editor`, for the overview the song page shows while Ctrl (Cmd on a
 * Mac) is held.
 *
 * **Shown, not handled, for now:** CodeMirror and our extensions still act on these keys; they move onto the key
 * manager in step 3. Until then this bag only lists them, reading the extensions' own constants where there are some
 * ([EditPlacesExtension.KEY]), so the overview and the editor cannot drift apart. A key the user changed for one of
 * these would show here but not act until step 3.
 */
object KlangEditorKeyBindings : KeyBindings {

    /** The scope of the editor's keys: they may share a key with the page's (`app`) actions. */
    const val SCOPE = "editor"

    /** Back to the place of the last edit (KLANG-00322). */
    val BackToLastEdit = KeyAction(
        id = "klang.editor.back-to-last-edit",
        title = "Back to the last edit",
        keys = listOf(EditPlacesExtension.KEY),
        scope = SCOPE,
    )

    /** Go to a bookmark, 0 to 9 (KLANG-00323): Ctrl on a Mac too, where Cmd with a digit switches the browser's tabs. */
    val GoToBookmark: List<KeyAction> = Bookmarks.DIGITS.map { digit ->
        KeyAction(
            id = "klang.editor.go-to-bookmark-$digit",
            title = "Go to bookmark $digit",
            keys = listOf("Ctrl-$digit"),
            scope = SCOPE,
        )
    }

    /** Set a bookmark, 0 to 9, on the cursor's line (KLANG-00323). */
    val SetBookmark: List<KeyAction> = Bookmarks.DIGITS.map { digit ->
        KeyAction(
            id = "klang.editor.set-bookmark-$digit",
            title = "Set bookmark $digit",
            keys = listOf("Ctrl-Shift-$digit"),
            scope = SCOPE,
        )
    }

    val Undo = KeyAction(id = "klang.editor.undo", title = "Undo", keys = listOf("Mod-Z"), scope = SCOPE)

    val Redo = KeyAction(
        id = "klang.editor.redo",
        title = "Redo",
        keys = listOf("Mod-Y", "Mod-Shift-Z"),
        macKeys = listOf("Mod-Shift-Z"),
        scope = SCOPE,
    )

    val LineComment = KeyAction(
        id = "klang.editor.line-comment",
        title = "Line comment",
        keys = listOf("Mod-/"),
        scope = SCOPE,
    )

    val Find = KeyAction(id = "klang.editor.find", title = "Find", keys = listOf("Mod-F"), scope = SCOPE)

    val Completion = KeyAction(
        id = "klang.editor.completion",
        title = "Completion",
        keys = listOf("Ctrl-Space"),
        scope = SCOPE,
    )

    override val actions: List<KeyAction> =
        listOf(BackToLastEdit) + GoToBookmark + SetBookmark + listOf(Undo, Redo, LineComment, Find, Completion)

    override val overview: List<KeyOverviewRow> = listOf(
        KeyOverviewRow.Single(BackToLastEdit),
        KeyOverviewRow.Group(KeyActionGroup(title = "Go to bookmark", actions = GoToBookmark)),
        KeyOverviewRow.Group(KeyActionGroup(title = "Set bookmark", actions = SetBookmark)),
        KeyOverviewRow.Single(Undo),
        KeyOverviewRow.Single(Redo),
        KeyOverviewRow.Single(LineComment),
        KeyOverviewRow.Single(Find),
        KeyOverviewRow.Single(Completion),
    )
}
