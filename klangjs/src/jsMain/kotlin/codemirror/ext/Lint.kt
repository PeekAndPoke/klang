/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
 * Calls [f] for every diagnostic currently held by the lint state, with the positions the state
 * has mapped them to through every document change since they were published.
 *
 * The `from` and `to` a linter source produced are therefore NOT what comes back here once the
 * document has moved on: these are the live ones.
 */
external fun forEachDiagnostic(state: EditorState, f: (diagnostic: Diagnostic, from: Int, to: Int) -> Unit)

/**
 * Runs a lint pass that is already scheduled right away, instead of waiting for the editor to
 * go idle.
 *
 * Only shortcuts a PENDING run: the lint plugin schedules a run when the document changes, when
 * the lint configuration changes, or when the `needsRefresh` callback of the config reports that
 * something else the sources depend on has moved. With nothing scheduled this is a no-op, so a
 * source whose input is not the document must announce the change through `needsRefresh` first.
 */
external fun forceLinting(view: EditorView)

/**
 * Command to open and focus the lint panel
 */
external val openLintPanel: dynamic

/**
 * Command to close the lint panel
 */
external val closeLintPanel: dynamic
