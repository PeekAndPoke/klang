/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.peekandpoke.klang.workshop.keys.KeyAction
import io.peekandpoke.klang.workshop.keys.KeyBindings
import org.w3c.dom.events.KeyboardEvent

/**
 * Klang's keys (KLANG-00325): the bag of actions the app defines with the key manager, with their default keys from
 * the PC preset decided in the key map interview (2026-10-21). The keys a user changes are kept by the app.
 *
 * [isUndo] and [isRedo] still name their keys themselves, for the mini-notation editor's own listener; they move onto
 * the key manager with the code editor (KLANG-00325 step 3).
 */
object KlangKeyBindings : KeyBindings {

    /** Starts the song, or updates it with the edited code while it plays: the song page's Play / Update button. */
    val PlayOrUpdate = KeyAction(id = "klang.play-or-update", title = "Play / Update", keys = listOf("Mod-Shift-Enter"))

    /** Stops the song: the song page's Stop button. */
    val Stop = KeyAction(id = "klang.stop", title = "Stop", keys = listOf("Mod-Shift-."))

    /** Answers a dialog with its main answer (the yes, the update), wherever the keyboard is in it. */
    val DialogConfirm = KeyAction(id = "klang.dialog.confirm", title = "Confirm", keys = listOf("Mod-Enter"))

    /** Leaves a dialog without an answer. */
    val DialogClose = KeyAction(id = "klang.dialog.close", title = "Close", keys = listOf("Escape"))

    override val actions: List<KeyAction> = listOf(PlayOrUpdate, Stop, DialogConfirm, DialogClose)

    /** Ctrl+Z (or Cmd+Z on Mac) — undo. */
    fun isUndo(e: KeyboardEvent): Boolean =
        (e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.lowercase() == "z"

    /** Ctrl+Shift+Z or Ctrl+Y (or Cmd+Shift+Z / Cmd+Y on Mac) — redo. */
    fun isRedo(e: KeyboardEvent): Boolean =
        (e.ctrlKey || e.metaKey) && (
                (e.shiftKey && e.key.lowercase() == "z") ||
                        (!e.shiftKey && e.key.lowercase() == "y")
                )
}
