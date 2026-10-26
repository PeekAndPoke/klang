/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:JsModule("@codemirror/search")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * The search panel and its state. Of the panel's own keys only Enter (next, Shift: previous) comes with it; the others
 * are a keymap's in the scope `search-panel` (CodeMirror's [searchKeymap], or the key map's in Klang's editor).
 */
external fun search(config: dynamic = definedExternally): Extension

/** Highlights the other occurrences of the selected word. */
external fun highlightSelectionMatches(options: dynamic = definedExternally): Extension

external val openSearchPanel: Command
external val closeSearchPanel: Command
external val findNext: Command
external val findPrevious: Command
external val replaceNext: Command
external val replaceAll: Command
external val selectNextOccurrence: Command
external val selectSelectionMatches: Command

/** Asks for a line number (a small dialog at the editor's top) and jumps there. */
external val gotoLine: Command

external val searchKeymap: Array<KeyBinding>

/** True while the search panel is open in [state]. */
external fun searchPanelOpen(state: EditorState): Boolean
