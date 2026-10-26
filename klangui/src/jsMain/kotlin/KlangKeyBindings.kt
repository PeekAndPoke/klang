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

    /**
     * Opens the search over everything (the new UI's search window, KLANG-00334): Ctrl+K (Cmd+K on a Mac), or Shift
     * tapped twice, as in IntelliJ (maintainer, 2026-10-25).
     */
    val SearchEverywhere = KeyAction(id = "klang.search-everywhere", title = "Search everywhere", keys = listOf("Mod-K", "Shift Shift"))

    /**
     * Closes the newest open panel: a tool window flying in, the Motor's drawer (the workbench, KLANG-00334; maintainer,
     * 2026-10-26). Each panel answers it in the page's layer while it is open; the last registered handler answers
     * first, so "the newest open" holds when a panel registers as it opens (a panel mounted all the time and only
     * enabled while open keeps its first place). Esc in a text field (the drawer's search) stays the field's own: a
     * plain key never acts there, so such a field closes its panel itself. The scopes of this action and
     * [FocusEditor] let the two share Escape on purpose (see there).
     */
    val PanelClose = KeyAction(
        id = "klang.panel.close",
        title = "Close",
        keys = listOf("Escape"),
        scope = "klang.panel",
    )

    /** Opens the Motor's drawer with the focus in its search field (KLANG-00334; Alt+M, the maintainer's OK). */
    val MotorMenu = KeyAction(id = "klang.motor-menu", title = "Motor menu", keys = listOf("Alt-M"))

    /**
     * Puts the focus back into the song's editor at its last cursor (maintainer, 2026-10-26: "One Esc close tool.
     * Another Esc focus editor"). Inside the editor Esc stays the editor's own, as a plain key in any text field.
     * Defined after [PanelClose] on purpose: both answer in the page's layer, where the first defined action with
     * an enabled handler wins, so an open panel closes first and the next Esc lands here. The two share Escape on
     * purpose, which their own scopes allow; one scope would report it as a clash. The price: a key a user gives either
     * of them that an `app` action holds already goes unreported, and the `app` action keeps it. A dialog with its own
     * key layer (YesNoModal) answers its Esc there; one without (a code tool) is guarded by the song page.
     */
    val FocusEditor = KeyAction(
        id = "klang.focus-editor",
        title = "Focus editor",
        keys = listOf("Escape"),
        scope = "klang.song-page",
    )

    override val actions: List<KeyAction> = listOf(
        PlayOrUpdate, Stop, DialogConfirm, DialogClose, SearchEverywhere, PanelClose, MotorMenu, FocusEditor,
    )

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
