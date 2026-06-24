/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.blocks.model

/**
 * The root of the blocks program model — an immutable, ordered list of top-level statements.
 *
 * A [KBProgram] is the single source of truth for the editor's content. All edits go
 * through KBProgramEditingCtx, which replaces the whole program value on every change
 * and pushes the previous value onto the undo stack.
 *
 * Serialisation: [KBProgram.toCode] converts the tree back to KlangScript source text.
 * Deserialisation: [AstToKBlocks.convert] builds a [KBProgram] from a parsed AST.
 */
data class KBProgram(
    val statements: List<KBStmt> = emptyList(),
)
