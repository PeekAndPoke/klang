/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.lang.parser.MnNode

// Staff position helpers moved to tones module: tones/note/NoteStaffPosition.kt
// Import them via: io.peekandpoke.klang.tones.note.staffPosition, staffPositionToNote, etc.

// ── Selection ─────────────────────────────────────────────────────────────────

/** The currently selected item in a note staff — identified by node [MnNode.id]. */
sealed interface MnSelection {
    val nodeId: Int

    data class Atom(val node: MnNode.Atom) : MnSelection {
        override val nodeId get() = node.id
    }

    data class Rest(val node: MnNode.Rest) : MnSelection {
        override val nodeId get() = node.id
    }
}

val MnSelection?.atom: MnNode.Atom? get() = (this as? MnSelection.Atom)?.node
val MnSelection?.rest: MnNode.Rest? get() = (this as? MnSelection.Rest)?.node

// ── String helpers ────────────────────────────────────────────────────────────

/**
 * Wraps a mini-notation string in the appropriate quote style for committing back to source.
 * Multi-line strings use backtick quotes; single-line strings use double quotes.
 */
internal fun String.quoteForCommit(): String =
    if (contains('\n')) "`$this`" else "\"$this\""
