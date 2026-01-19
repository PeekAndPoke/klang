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

// Completion commands
external val acceptCompletion: Command
external val startCompletion: Command
external val closeCompletion: Command

// Search commands
external val openSearchPanel: Command
external val closeSearchPanel: Command
external val selectNextOccurrence: Command
external val selectSelectionMatches: Command
external val findNext: Command
external val findPrevious: Command
external val replaceNext: Command
external val replaceAll: Command

// Fold commands
external val foldCode: Command
external val unfoldCode: Command
external val foldAll: Command
external val unfoldAll: Command

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

// Completion
external interface CompletionConfig {
    var activateOnTyping: Boolean?
    var override: Array<dynamic>?
    var maxRenderedOptions: Int?
    var defaultKeymap: Boolean?
    var aboveCursor: Boolean?
    var icons: Boolean?
    var addToOptions: Array<dynamic>?
    var compareCompletions: ((a: dynamic, b: dynamic) -> Int)?
    var selectOnOpen: Boolean?
}

// Comment config
external interface CommentConfig {
    var lineComment: String?
    var blockCommentStart: String?
    var blockCommentEnd: String?
}

external val commentKeymap: Array<KeyBinding>
external val completionKeymap: Array<KeyBinding>
external val searchKeymap: Array<KeyBinding>
external val foldKeymap: Array<KeyBinding>

// Utility for creating custom commands
external interface CommandContext {
    val state: EditorState
    val dispatch: (transaction: Transaction) -> Unit
}
