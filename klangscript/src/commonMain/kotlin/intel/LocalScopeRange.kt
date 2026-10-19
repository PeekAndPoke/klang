/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * One lexical scope as [AnalyzedAst] walked it, with its place in the source: the program, an arrow body, an `if` or
 * `else` block, a loop. [AnalyzedAst.localsAt] reads the tree of these to find the locals visible at a cursor.
 *
 * [start] is the offset of the token that opens the scope (`=>`, `if`, `while`, `do`, `for`; the end of the `if`
 * block for an `else` block; 0 for the program). [end] is the end of the last token the walk met inside the scope.
 * The AST records no closing brace, so [end] can stop short of a block's `}`.
 *
 * Filled while the analysis walks the program and never changed afterwards.
 */
internal class LocalScopeRange(
    val start: Int,
    val parent: LocalScopeRange?,
) {
    /** The end (exclusive) of the last token met inside this scope, nested scopes included. */
    var end: Int = start
        private set

    /** The bindings this scope declares, in source order. */
    val bindings = mutableListOf<TypeScope.LocalBinding>()

    /** The scopes nested directly in this one, in source order. */
    val children = mutableListOf<LocalScopeRange>()

    /** Widen [end] to [offset] when the walk meets a token that ends there. */
    fun extendTo(offset: Int) {
        if (offset > end) {
            end = offset
        }
    }

    /**
     * The nested scope that holds the whole stretch from [from] to [to], or null. A cursor right after a scope's last
     * token is still inside it (it is typing on), a cursor on its opening token is not.
     */
    fun childContaining(from: Int, to: Int): LocalScopeRange? = children.firstOrNull { from > it.start && to <= it.end }
}
