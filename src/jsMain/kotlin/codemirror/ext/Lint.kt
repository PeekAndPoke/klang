@file:JsModule("@codemirror/lint")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * A diagnostic message from linting
 */
external interface Diagnostic {
    var from: Int
    var to: Int
    var severity: String  // "error", "warning", "info"
    var message: String
    var source: String?
    var actions: Array<Action>?
}

/**
 * An action that can be taken to fix a diagnostic
 */
external interface Action {
    var name: String
    var apply: (view: EditorView, from: Int, to: Int) -> Unit
}

/**
 * Set diagnostics for an editor state
 *
 * Returns a TransactionSpec that can be dispatched to update diagnostics
 */
external fun setDiagnostics(state: EditorState, diagnostics: Array<Diagnostic>): dynamic

/**
 * Lint extension with just a source function
 */
external fun linter(source: (view: EditorView) -> Array<Diagnostic>): Extension

/**
 * Lint extension with source and config
 * @param source The linter source function (or null)
 * @param config Configuration object with properties like autoPanel, delay, etc.
 */
external fun linter(source: (view: EditorView) -> Array<Diagnostic>, config: dynamic): Extension

/**
 * The lint gutter extension - shows diagnostic markers in the gutter
 */
external fun lintGutter(): Extension

/**
 * Command to open and focus the lint panel
 */
external val openLintPanel: dynamic

/**
 * Command to close the lint panel
 */
external val closeLintPanel: dynamic
