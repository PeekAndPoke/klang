/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangKeyPriorities
import io.peekandpoke.klang.workshop.keys.KeyAction
import io.peekandpoke.klang.workshop.keys.KeyActionGroup
import io.peekandpoke.klang.workshop.keys.KeyBindings
import io.peekandpoke.klang.workshop.keys.KeyOverviewRow

/**
 * The code editor's keys (KLANG-00325), in the scope `editor`: the PC and Mac presets of the key map interview
 * (2026-10-21). The editor answers only these keys and the plain text keys (arrows, Home, Backspace, Enter: CodeMirror's
 * `standardKeymap`); [EditorKeyTable] runs each action's commands. The song page shows [overview] while Ctrl (Cmd on a
 * Mac) is held.
 *
 * Several actions may share a key in this scope when their priorities differ: the completion list's keys
 * ([KlangKeyPriorities.EditorPopup]) go first, and pass the key on while no list is open.
 */
object KlangEditorKeyBindings : KeyBindings {

    /** The scope of the editor's keys: they may share a key with the page's (`app`) actions. */
    const val SCOPE = "editor"

    private fun action(id: String, title: String, keys: List<String>, macKeys: List<String>? = null) =
        KeyAction(id = "klang.editor.$id", title = title, keys = keys, macKeys = macKeys, scope = SCOPE)

    // Lines

    val MoveLineUp = action(id = "move-line-up", title = "Move line up", keys = listOf("Mod-Shift-ArrowUp"))

    val MoveLineDown = action(id = "move-line-down", title = "Move line down", keys = listOf("Mod-Shift-ArrowDown"))

    val DuplicateLine = action(id = "duplicate-line", title = "Duplicate line", keys = listOf("Mod-D"))

    val StartNewLine = action(id = "start-new-line", title = "Start new line", keys = listOf("Shift-Enter"))

    val SplitLine = action(id = "split-line", title = "Split line", keys = listOf("Mod-Enter"))

    val Indent = action(id = "indent", title = "Indent", keys = listOf("Tab"))

    val Unindent = action(id = "unindent", title = "Unindent", keys = listOf("Shift-Tab"))

    val LineComment = action(id = "line-comment", title = "Line comment", keys = listOf("Mod-/"))

    val BlockComment = action(id = "block-comment", title = "Block comment", keys = listOf("Mod-Shift-/"), macKeys = listOf("Mod-Alt-/"))

    val Reformat = action(id = "reformat", title = "Reformat", keys = listOf("Ctrl-Alt-L"), macKeys = listOf("Mod-Alt-L"))

    // Selection and cursors

    val SelectNextOccurrence =
        action(id = "select-next-occurrence", title = "Select next occurrence", keys = listOf("Alt-J"), macKeys = listOf("Ctrl-G"))

    val UnselectOccurrence = action(
        id = "unselect-occurrence",
        title = "Unselect last occurrence",
        keys = listOf("Alt-Shift-J"),
        macKeys = listOf("Ctrl-Shift-G"),
    )

    val SelectAllOccurrences = action(
        id = "select-all-occurrences",
        title = "Select all occurrences",
        keys = listOf("Ctrl-Alt-Shift-J"),
        macKeys = listOf("Ctrl-Meta-G"),
    )

    val ExpandSelection =
        action(id = "expand-selection", title = "Expand selection", keys = listOf("Mod-I"), macKeys = listOf("Alt-ArrowUp", "Mod-I"))

    val AddCursorAbove =
        action(id = "add-cursor-above", title = "Add cursor above", keys = listOf("Ctrl-Alt-ArrowUp"), macKeys = listOf("Mod-Alt-ArrowUp"))

    val AddCursorBelow = action(
        id = "add-cursor-below",
        title = "Add cursor below",
        keys = listOf("Ctrl-Alt-ArrowDown"),
        macKeys = listOf("Mod-Alt-ArrowDown"),
    )

    val SelectLine = action(id = "select-line", title = "Select line", keys = listOf("Alt-L"), macKeys = listOf("Ctrl-L"))

    val MatchingBracket =
        action(id = "matching-bracket", title = "Matching bracket", keys = listOf("Mod-Shift-M"), macKeys = listOf("Ctrl-M"))

    /** Back to one cursor; with one already, Esc closes the search panel, else it goes on to the page. */
    val BackToOneCursor = action(id = "back-to-one-cursor", title = "Back to one cursor", keys = listOf("Escape"))

    // Search

    val Find = action(id = "find", title = "Find", keys = listOf("Mod-F"))

    val Replace = action(id = "replace", title = "Replace", keys = listOf("Mod-R"))

    val FindNext = action(id = "find-next", title = "Next match", keys = listOf("F3"), macKeys = listOf("Mod-G"))

    val FindPrevious = action(id = "find-previous", title = "Previous match", keys = listOf("Shift-F3"), macKeys = listOf("Mod-Shift-G"))

    val GoToLine = action(id = "go-to-line", title = "Go to line", keys = listOf("Mod-G"), macKeys = listOf("Mod-L"))

    // History

    val Undo = action(id = "undo", title = "Undo", keys = listOf("Mod-Z"))

    val Redo = action(id = "redo", title = "Redo", keys = listOf("Mod-Y", "Mod-Shift-Z"), macKeys = listOf("Mod-Shift-Z"))

    // Completion: its list's keys stand in front of the editor's own

    val Completion = action(id = "completion", title = "Completion", keys = listOf("Ctrl-Space"))

    val AcceptCompletion = action(id = "accept-completion", title = "Accept completion", keys = listOf("Enter", "Tab"))
        .copy(priority = KlangKeyPriorities.EditorPopup)

    val CloseCompletion = action(id = "close-completion", title = "Close completion", keys = listOf("Escape"))
        .copy(priority = KlangKeyPriorities.EditorPopup)

    // Problems

    val NextProblem = action(id = "next-problem", title = "Next problem", keys = listOf("F2"))

    val PreviousProblem = action(id = "previous-problem", title = "Previous problem", keys = listOf("Shift-F2"))

    // Moving around

    /** A local to its declaration; a library name to its docs page (KLANG-00321, also Ctrl+click). */
    val GoToDefinition = action(id = "go-to-definition", title = "Go to definition", keys = listOf("Mod-B"))

    /**
     * The help of the name at the cursor: in the host's Help window when it has one (the song page), else the docs
     * popup as hovering shows it. Alt+Q on every platform (the maintainer, 2026-10-26, KLANG-00349; Ctrl+Q quits
     * Firefox on Linux, and a Mac's Option+Q matches by position).
     */
    val QuickDocumentation = action(id = "quick-documentation", title = "Quick documentation", keys = listOf("Alt-Q"))

    /** Back to the place of the last edit (KLANG-00322). */
    val BackToLastEdit = action(id = "back-to-last-edit", title = "Back to the last edit", keys = listOf(EditPlacesExtension.KEY))

    /** Go to a bookmark, 0 to 9 (KLANG-00323): Ctrl on a Mac too, where Cmd with a digit switches the browser's tabs. */
    val GoToBookmark: List<KeyAction> = Bookmarks.DIGITS.map { digit ->
        action(id = "go-to-bookmark-$digit", title = "Go to bookmark $digit", keys = listOf("Ctrl-$digit"))
    }

    /** Set a bookmark, 0 to 9, on the cursor's line (KLANG-00323). */
    val SetBookmark: List<KeyAction> = Bookmarks.DIGITS.map { digit ->
        action(id = "set-bookmark-$digit", title = "Set bookmark $digit", keys = listOf("Ctrl-Shift-$digit"))
    }

    /** The action that jumps to bookmark [digit]; null for a digit no bookmark can have. */
    fun goToBookmark(digit: Int): KeyAction? = GoToBookmark.firstOrNull { it.id == "klang.editor.go-to-bookmark-$digit" }

    /**
     * Tab moves the focus out of the editor for good, until pressed again (CodeMirror's key for keyboard users; Esc,
     * then Tab does it once).
     */
    val TabMovesFocus =
        action(id = "tab-moves-focus", title = "Tab moves the focus", keys = listOf("Ctrl-M"), macKeys = listOf("Alt-Shift-M"))

    override val actions: List<KeyAction> = listOf(
        MoveLineUp, MoveLineDown, DuplicateLine, StartNewLine, SplitLine, Indent, Unindent, LineComment, BlockComment,
        Reformat,
        SelectNextOccurrence, UnselectOccurrence, SelectAllOccurrences, ExpandSelection, AddCursorAbove, AddCursorBelow,
        SelectLine, MatchingBracket, BackToOneCursor,
        Find, Replace, FindNext, FindPrevious, GoToLine,
        Undo, Redo,
        Completion, AcceptCompletion, CloseCompletion,
        NextProblem, PreviousProblem,
        GoToDefinition, QuickDocumentation, BackToLastEdit,
    ) + GoToBookmark + SetBookmark + listOf(TabMovesFocus)

    /**
     * The overview, by function (maintainer, 2026-10-26: "start to organize a bit by function groups, it becomes hard
     * to see at once"): moving around, editing, selecting, finding and help. Left out: the completion list's keys
     * (Enter, Tab, Esc), which show where they act, and Tab moves the focus, a rare toggle for keyboard users.
     */
    override val overview: List<KeyOverviewRow> = listOf(
        KeyOverviewRow.Heading("Navigate"),
        KeyOverviewRow.Single(BackToLastEdit),
        KeyOverviewRow.Group(KeyActionGroup(title = "Go to bookmark", actions = GoToBookmark)),
        KeyOverviewRow.Group(KeyActionGroup(title = "Set bookmark", actions = SetBookmark)),
        KeyOverviewRow.Single(GoToLine),
        KeyOverviewRow.Single(GoToDefinition),
        KeyOverviewRow.Single(MatchingBracket),
        KeyOverviewRow.Single(NextProblem),
        KeyOverviewRow.Single(PreviousProblem),
        // The song page's key that brings the keyboard back here (KlangKeyBindings, maintainer 2026-10-26); only the
        // song page shows this overview, and only it handles the key
        KeyOverviewRow.Single(KlangKeyBindings.FocusEditor),

        KeyOverviewRow.Heading("Edit"),
        KeyOverviewRow.Single(Undo),
        KeyOverviewRow.Single(Redo),
        KeyOverviewRow.Single(DuplicateLine),
        KeyOverviewRow.Single(MoveLineUp),
        KeyOverviewRow.Single(MoveLineDown),
        KeyOverviewRow.Single(StartNewLine),
        KeyOverviewRow.Single(SplitLine),
        KeyOverviewRow.Single(Indent),
        KeyOverviewRow.Single(Unindent),
        KeyOverviewRow.Single(LineComment),
        KeyOverviewRow.Single(BlockComment),
        KeyOverviewRow.Single(Reformat),

        KeyOverviewRow.Heading("Select"),
        KeyOverviewRow.Single(SelectNextOccurrence),
        KeyOverviewRow.Single(UnselectOccurrence),
        KeyOverviewRow.Single(SelectAllOccurrences),
        KeyOverviewRow.Single(ExpandSelection),
        KeyOverviewRow.Single(SelectLine),
        KeyOverviewRow.Single(AddCursorAbove),
        KeyOverviewRow.Single(AddCursorBelow),
        KeyOverviewRow.Single(BackToOneCursor),

        KeyOverviewRow.Heading("Find and help"),
        KeyOverviewRow.Single(Find),
        KeyOverviewRow.Single(Replace),
        KeyOverviewRow.Single(FindNext),
        KeyOverviewRow.Single(FindPrevious),
        KeyOverviewRow.Single(Completion),
        KeyOverviewRow.Single(QuickDocumentation),
    )
}
