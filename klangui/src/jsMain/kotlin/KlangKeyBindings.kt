/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.peekandpoke.klang.workshop.keys.KeyAction
import io.peekandpoke.klang.workshop.keys.KeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPriority

/**
 * Klang's key priorities, all of them in this one place, lowest first (KLANG-00325, maintainer 2026-10-26: "manage
 * these priorities in one central place, so we always see what is already there"). When several actions in the top
 * layer answer the same key, the highest priority answers, within one priority the newest registration: so Esc closes
 * a code tool, then a panel, then puts the focus back into the editor, whatever mounted first. Gaps of ten leave room
 * for a level between two. Every Klang action takes one of [all]; the keys specs check it.
 */
object KlangKeyPriorities {
    /** The page's own keys: Esc puts the focus back into the editor. */
    val Page = KeyPriority.Default

    /** A panel in front of the page: a tool window flying in, the Motor's drawer. */
    val Panel = KeyPriority(value = 10, name = "panel")

    /** A code tool or a dialog in front of a panel. */
    val Tool = KeyPriority(value = 20, name = "tool")

    /**
     * In the code editor's own scope: a list open in the editor (the completion) in front of the editor's keys, so
     * Enter accepts before it breaks the line, Tab before it indents and Esc closes the list before anything else.
     */
    val EditorPopup = KeyPriority(value = 10, name = "editor-popup")

    /** Every priority Klang uses: the page's, lowest first, then the editor's scope. */
    val all = listOf(Page, Panel, Tool, EditorPopup)
}

/**
 * Klang's keys (KLANG-00325): the bag of actions the app defines with the key manager, with their default keys from
 * the PC preset decided in the key map interview (2026-10-21). The keys a user changes are kept by the app.
 */
object KlangKeyBindings : KeyBindings {

    /** Starts the song, or updates it with the edited code while it plays: the song page's Play / Update button. */
    val PlayOrUpdate = KeyAction(id = "klang.play-or-update", title = "Play / Update", keys = listOf("Mod-Shift-Enter"))

    /** Stops the song: the song page's Stop button. */
    val Stop = KeyAction(id = "klang.stop", title = "Stop", keys = listOf("Mod-Shift-."))

    /** Answers a dialog with its main answer (the yes, the update), wherever the keyboard is in it. */
    val DialogConfirm = KeyAction(id = "klang.dialog.confirm", title = "Confirm", keys = listOf("Mod-Enter"))

    /**
     * Leaves a dialog without an answer; a code tool's Esc is its Cancel. In front of a panel and the page
     * ([KlangKeyPriorities.Tool]); a dialog with its own key layer (KlangYesNoModal) answers it there anyway.
     */
    val DialogClose = KeyAction(
        id = "klang.dialog.close",
        title = "Close",
        keys = listOf("Escape"),
        priority = KlangKeyPriorities.Tool,
    )

    /**
     * Opens the search over everything (the new UI's search window, KLANG-00334): Ctrl+K (Cmd+K on a Mac), or Shift
     * tapped twice, as in IntelliJ (maintainer, 2026-10-25).
     */
    val SearchEverywhere = KeyAction(id = "klang.search-everywhere", title = "Search everywhere", keys = listOf("Mod-K", "Shift Shift"))

    /**
     * Closes the newest open panel: a tool window flying in, the Motor's drawer (the workbench, KLANG-00334; maintainer,
     * 2026-10-26). Each panel registers it while it is open, in the page's layer; of two
     * open panels the newer closes first. In front of the page, behind a code tool ([KlangKeyPriorities.Panel]). Also
     * from a text field (the code editor), unless the field uses its Esc itself (the editor closing its completion).
     */
    val PanelClose = KeyAction(
        id = "klang.panel.close",
        title = "Close",
        keys = listOf("Escape"),
        priority = KlangKeyPriorities.Panel,
    )

    /** Opens the Motor's drawer with the focus in its search field (KLANG-00334; Alt+M, the maintainer's OK). */
    val MotorMenu = KeyAction(id = "klang.motor-menu", title = "Motor menu", keys = listOf("Alt-M"))

    /**
     * Puts the focus back into the song's editor at its last cursor (maintainer, 2026-10-26: "One Esc close tool.
     * Another Esc focus editor"). An Esc the editor uses (closing its completion) stays the editor's own. The
     * page's own key ([KlangKeyPriorities.Page]): an open code tool or panel answers Esc first, also one that was open
     * before the song page mounted.
     */
    val FocusEditor = KeyAction(
        id = "klang.focus-editor",
        title = "Focus editor",
        keys = listOf("Escape"),
        priority = KlangKeyPriorities.Page,
    )

    /**
     * Puts the focus on the Motor's volume knob, so the arrow keys turn it (KLANG-00344; maintainer, 2026-10-26: "The
     * volume knob could also use a shortcut like Alt+V maybe to select the knob").
     */
    val VolumeFocus = KeyAction(
        id = "klang.focus-volume",
        title = "Volume",
        keys = listOf("Alt-V"),
        priority = KlangKeyPriorities.Page,
    )

    /**
     * Undo in a code tool's own editor (the mini-notation editor), in front of the page's keys while the tool is open
     * and has something to undo. The code editor's own undo (its scope's), when it has the focus and something to undo,
     * answers first and keeps the key.
     */
    val ToolUndo = KeyAction(id = "klang.tool.undo", title = "Undo", keys = listOf("Mod-Z"), priority = KlangKeyPriorities.Tool)

    /** Redo in a code tool's own editor, as the code editor's keys: Ctrl+Y or Ctrl+Shift+Z, Cmd+Shift+Z on a Mac. */
    val ToolRedo = KeyAction(
        id = "klang.tool.redo",
        title = "Redo",
        keys = listOf("Mod-Y", "Mod-Shift-Z"),
        macKeys = listOf("Mod-Shift-Z"),
        priority = KlangKeyPriorities.Tool,
    )

    override val actions: List<KeyAction> = listOf(
        PlayOrUpdate, Stop, DialogConfirm, DialogClose, SearchEverywhere, PanelClose, MotorMenu, FocusEditor, VolumeFocus,
        ToolUndo, ToolRedo,
    )
}
