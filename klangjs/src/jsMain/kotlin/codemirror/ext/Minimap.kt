/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:JsModule("@replit/codemirror-minimap")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Facet that shows a minimap (a scaled-down picture of the whole document) in the right gutter.
 *
 * The minimap mounts into [EditorView.scrollDOM] and tracks its scroll position, so the editor
 * must own its scrolling: its theme sets the editor to `height: 100%`, and every ancestor needs
 * a definite height. Providing `null` hides it.
 */
external val showMinimap: Facet<MinimapConfig?>

/**
 * Minimap configuration
 */
external interface MinimapConfig {
    /** Creates the element that holds the minimap; the extension adds its own classes to it. */
    var create: (view: EditorView) -> dynamic

    /** `"blocks"` or `"characters"` (the package default). */
    var displayText: String?

    /** `"always"` (the package default) or `"mouse-over"`. */
    var showOverlay: String?

    /** Coloured marks left of the minimap: one map per gutter, line number to CSS colour. */
    var gutters: Array<dynamic>?
}
