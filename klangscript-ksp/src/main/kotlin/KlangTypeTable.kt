/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

/**
 * The distinct `KlangType(...)` expressions of one library's generated docs, each emitted once as a
 * private top-level `val` and referenced by name everywhere else.
 *
 * The docs used a few thousand `KlangType` constructions for about a hundred distinct types per library
 * (2026-10-06: 102 in sprudel, 109 in the stdlib; see docs/tasks/reduce-js-bundle-size.md), about a tenth
 * of the production bundle. A `KlangType` is an immutable data class, so sharing one instance changes
 * nothing a reader of the docs can observe.
 *
 * Nested types are interned first (an expression is built from the references of its parts), so a
 * table entry only ever refers to entries declared before it.
 */
internal class KlangTypeTable {
    private val names = LinkedHashMap<String, String>()

    /** The name of the table entry for [expression], added on first use. */
    fun ref(expression: String): String = names.getOrPut(expression) { "kt${names.size}" }

    /** One `private val ktN = KlangType(...)` line per entry, in the order they were added. */
    fun render(): String = buildString {
        names.forEach { (expression, name) -> appendLine("private val $name = $expression") }
    }
}
