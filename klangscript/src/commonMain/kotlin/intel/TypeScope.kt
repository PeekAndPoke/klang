/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.types.KlangType

/**
 * Lexical type environment used by [AnalyzedAst] to track local bindings
 * (`let` / `const` / `export` / arrow-function parameters) as it walks the AST.
 *
 * Mirrors the interpreter's `runtime/Environment.kt` lookup rules: an inner
 * scope shadows any binding of the same name in an outer scope, and a local
 * binding shadows any same-named symbol registered in the docs registry.
 *
 * Important: [contains] returns `true` even when the bound [LocalBinding.type]
 * is `null`. "Bound but type unknown" is meaningfully different from "not
 * bound" — only the former should shadow the registry.
 */
class TypeScope(private val parent: TypeScope? = null) {

    /**
     * A locally bound name and its inferred type. [type] may be `null` if the
     * initializer's type could not be inferred (e.g. an arrow function literal
     * or an expression we don't yet track). The binding still shadows any
     * registry symbol with the same name.
     *
     * @param declPos Source offset of the declaration site, when available.
     *   Used by `AnalyzedAst.symbolAt` to attach declaration context to the
     *   synthesised local-symbol popup.
     */
    data class LocalBinding(
        val name: String,
        val type: KlangType?,
        val kind: io.peekandpoke.klang.script.types.KlangSymbol.LocalKind,
        val declPos: Int? = null,
    )

    private val bindings = mutableMapOf<String, LocalBinding>()

    fun bind(binding: LocalBinding) {
        bindings[binding.name] = binding
    }

    /** Innermost binding for [name], walking up parent scopes. `null` if unbound anywhere. */
    fun resolve(name: String): LocalBinding? {
        return bindings[name] ?: parent?.resolve(name)
    }

    /** `true` if [name] is bound in this scope or any parent — even when the bound type is unknown. */
    fun contains(name: String): Boolean {
        return bindings.containsKey(name) || (parent?.contains(name) == true)
    }

    /** Open a fresh inner scope (e.g. when entering a function body or block). */
    fun child(): TypeScope = TypeScope(this)
}
