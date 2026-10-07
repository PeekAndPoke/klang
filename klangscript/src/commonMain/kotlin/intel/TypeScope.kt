/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Lexical type environment used by [AnalyzedAst] to track local bindings
 * (`let` / `const` / `export` / arrow-function parameters) as it walks the AST.
 *
 * Mirrors the interpreter's `runtime/Environment.kt` lookup rules: an inner
 * scope shadows any binding of the same name in an outer scope, and a local
 * binding shadows any same-named symbol registered in the docs registry.
 *
 * Important: [resolve] returns a binding even when its [LocalBinding.type]
 * is `null`. "Bound but type unknown" is meaningfully different from "not
 * bound": only the former should shadow the registry.
 */
class TypeScope(
    private val parent: TypeScope? = null,
    /** True for the scope of a function body that runs later than where it is written ([deferredBody]). */
    private val isDeferredBody: Boolean = false,
) {

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
        val kind: KlangSymbol.LocalKind,
        val declPos: Int? = null,
    )

    private val bindings = mutableMapOf<String, LocalBinding>()

    /** A name a statement of this scope declares further down ([declareAhead]), and the identifiers that read it early. */
    private class Ahead(val binding: LocalBinding) {
        val readers = mutableListOf<Identifier>()
    }

    /** Names a statement of this scope declares further down, not yet bound ([declareAhead]). */
    private val declaredAhead = mutableMapOf<String, Ahead>()

    /**
     * Bind [binding] in this scope. Returns the identifiers that read the name before the declaration, from a deferred
     * body ([resolveReference]): they were bound with the type unknown, and the caller gives them this binding.
     */
    fun bind(binding: LocalBinding): List<Identifier> {
        bindings[binding.name] = binding

        return declaredAhead.remove(binding.name)?.readers ?: emptyList()
    }

    /**
     * Announce that a statement of this scope declares [name] further down. Code written before the declaration
     * does not see it: the interpreter defines a `let` / `const` when it reaches it, and until then the name is
     * whatever an outer scope or a library holds. A deferred body ([deferredBody]) written before it does: it runs
     * later, and the interpreter looks its names up in the scope it was created in when it runs, so a function bound
     * by a declaration and called once the scope has run sees the local (`const f = () => gain(1); const gain = ...`).
     */
    fun declareAhead(name: String, kind: KlangSymbol.LocalKind) {
        declaredAhead[name] = Ahead(LocalBinding(name = name, type = null, kind = kind))
    }

    /**
     * Innermost binding for [name], walking up parent scopes. `null` if unbound anywhere; a binding whose type is
     * unknown still shadows the registry. A name declared further down ([declareAhead]) resolves with its type unknown
     * until the declaration is reached ([bind]), by the rule (`ref/intel-analyzer.md`, "Locals declared further down"):
     * A scope's later locals are visible to everything written inside an arrow that is a direct `let` / `const` /
     * `export` initialiser of that scope, eager bodies nested in it included (they run when the arrow runs, or
     * later), and to nothing else: a block, an IIFE or a call-argument body that is not inside such an arrow of
     * that scope finishes before the scope reaches the declaration.
     * In code: a scope checks its own later declarations only when the walk comes from a [deferredBody] child.
     */
    fun resolve(name: String): LocalBinding? = resolve(name, reader = null, fromDeferredBody = false)

    /** [resolve] for the identifier [reader], remembered when it reads a name declared further down ([bind] returns it). */
    fun resolveReference(reader: Identifier): LocalBinding? = resolve(reader.name, reader = reader, fromDeferredBody = false)

    /** [fromDeferredBody]: the walk comes from a deferred body declared in this scope. */
    private fun resolve(name: String, reader: Identifier?, fromDeferredBody: Boolean): LocalBinding? {
        bindings[name]?.let { return it }

        if (fromDeferredBody) {
            declaredAhead[name]?.let { ahead ->
                reader?.let { ahead.readers.add(it) }

                return ahead.binding
            }
        }

        return parent?.resolve(name, reader, fromDeferredBody = isDeferredBody)
    }

    /** Open a fresh inner scope: a block, or a function body that runs where it is written (a call argument). */
    fun child(): TypeScope = TypeScope(this)

    /**
     * Open the scope of a function body that runs later than where it is written: an arrow that is the whole
     * initialiser of a `let` / `const` / `export`.
     */
    fun deferredBody(): TypeScope = TypeScope(this, isDeferredBody = true)
}
