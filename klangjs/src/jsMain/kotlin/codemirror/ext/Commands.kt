/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:JsModule("@codemirror/commands")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

import org.w3c.dom.events.KeyboardEvent

/**
 * Command function type - returns true if it handled the command
 */
typealias Command = (view: EditorView) -> Boolean

/**
 * Command with keyboard event
 */
typealias CommandWithEvent = (view: EditorView, event: KeyboardEvent) -> Boolean

// Basic editing commands
external val deleteCharBackward: Command
external val deleteCharForward: Command
external val deleteWordBackward: Command
external val deleteWordForward: Command
external val deleteGroupBackward: Command
external val deleteGroupForward: Command
external val deleteToLineStart: Command
external val deleteToLineEnd: Command
external val deleteTrailingWhitespace: Command

external val splitLine: Command
external val transposeChars: Command

// Selection commands
external val selectAll: Command
external val selectLine: Command
external val selectParentSyntax: Command
external val selectSyntaxTreeAtCursor: Command
external val selectLineStart: Command
external val selectLineEnd: Command

external val cursorCharLeft: Command
external val cursorCharRight: Command
external val cursorGroupLeft: Command
external val cursorGroupRight: Command
external val cursorSubwordBackward: Command
external val cursorSubwordForward: Command
external val cursorSyntaxLeft: Command
external val cursorSyntaxRight: Command

external val cursorLineStart: Command
external val cursorLineEnd: Command
external val cursorDocStart: Command
external val cursorDocEnd: Command
external val cursorPageDown: Command
external val cursorPageUp: Command
external val cursorLineBoundaryBackward: Command
external val cursorLineBoundaryForward: Command

external val cursorMatchingBracket: Command
external val selectMatchingBracket: Command

// Line commands
external val cursorLineUp: Command
external val cursorLineDown: Command
external val moveLineUp: Command
external val moveLineDown: Command
external val copyLineUp: Command
external val copyLineDown: Command
external val deleteLine: Command

// Indentation commands
external val indentMore: Command
external val indentLess: Command
external val indentSelection: Command
external val indentWithTab: Command

// History commands
external val undo: Command
external val redo: Command
external val undoSelection: Command
external val redoSelection: Command

// Comment commands
external val toggleComment: Command
external val toggleLineComment: Command
external val toggleBlockComment: Command
external val lineComment: Command
external val lineUncomment: Command
external val blockComment: Command
external val blockUncomment: Command

// Multiple cursors
external val addCursorAbove: Command
external val addCursorBelow: Command
external val simplifySelection: Command

// Tab focus mode: Tab moves the focus out of the editor instead of indenting
external val toggleTabFocusMode: Command

// Misc commands
external val insertNewline: Command
external val insertNewlineAndIndent: Command
external val insertBlankLine: Command
external val insertTab: Command

// Command composition
external fun chainCommands(vararg commands: Command): Command

// Standard keymaps
external val defaultKeymap: Array<KeyBinding>
external val standardKeymap: Array<KeyBinding>
external val historyKeymap: Array<KeyBinding>
external val indentWithTabKeymap: Array<KeyBinding>

// History
external val history: (config: dynamic) -> Extension
external val historyField: dynamic

/** Effects the history stores with a transaction and replays when it undoes it (and when it redoes the undo). */
external val invertedEffects: Facet<(tr: Transaction) -> Array<StateEffect<*>>>

/** Keeps a transaction from joining the history events around it: `"before"`, `"after"` or `"full"`. */
external val isolateHistory: AnnotationType<String>

// Comment config
external interface CommentConfig {
    var lineComment: String?
    var blockCommentStart: String?
    var blockCommentEnd: String?
}

// Utility for creating custom commands
external interface CommandContext {
    val state: EditorState
    val dispatch: (transaction: Transaction) -> Unit
}
