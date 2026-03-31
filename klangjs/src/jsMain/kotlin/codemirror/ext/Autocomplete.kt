@file:JsModule("@codemirror/autocomplete")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Creates an autocompletion extension.
 */
external fun autocompletion(config: CompletionConfig = definedExternally): Extension

/**
 * Completion configuration
 */
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

/**
 * Completion context — passed to completion source functions.
 */
external interface CompletionContext {
    val state: EditorState
    val pos: Int
    val explicit: Boolean
    val aborted: Boolean

    fun matchBefore(regexp: dynamic): dynamic
    val abortListeners: Array<() -> Unit>
}

/**
 * Completion result returned by a completion source.
 */
external interface CompletionResult {
    var from: Int
    var to: Int?
    var options: Array<Completion>
    var span: dynamic
    var filter: Boolean?
    var validFor: dynamic
}

/**
 * Single completion option.
 */
external interface Completion {
    var label: String
    var detail: String?
    var info: Any? // string or function returning DOM/string
    var apply: Any? // string or function
    var type: String?
    var boost: Int?
    var section: Any? // string or CompletionSection
}

/**
 * Completion section for grouping completions.
 */
external interface CompletionSection {
    var name: String
    var header: Any? // function returning Element
    var rank: Int?
}

// Completion commands
external val acceptCompletion: Command
external val startCompletion: Command
external val closeCompletion: Command

// Completion keymap
external val completionKeymap: Array<KeyBinding>

// Utility functions
external fun completeFromList(list: Array<Completion>): (context: CompletionContext) -> CompletionResult?
external fun completionStatus(state: EditorState): String? // "active" | "pending" | null
external fun currentCompletions(state: EditorState): Array<Completion>
external fun selectedCompletion(state: EditorState): Completion?

// Snippet support
external fun snippet(template: String): (editor: EditorView, completion: Completion, from: Int, to: Int) -> Unit
external fun snippetCompletion(template: String, completion: Completion): Completion

// Close brackets
external fun closeBrackets(): Extension
external val closeBracketsKeymap: Array<KeyBinding>
