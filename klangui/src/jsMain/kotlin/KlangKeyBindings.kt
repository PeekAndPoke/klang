package io.peekandpoke.klang.ui

import org.w3c.dom.events.KeyboardEvent

/**
 * Centralised keyboard-shortcut definitions for the Klang UI.
 *
 * Use these helpers wherever keyboard shortcuts are handled so that all
 * tools (mini-notation editor, block editor, …) react to the same keys.
 */
object KlangKeyBindings {

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
