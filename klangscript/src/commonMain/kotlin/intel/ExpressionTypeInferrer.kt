/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.BooleanLiteral
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.ast.NullLiteral
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.ast.ObjectLiteral
import io.peekandpoke.klang.script.ast.StringLiteral
import io.peekandpoke.klang.script.ast.TemplateLiteral
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangType

/**
 * Lightweight static type inferrer that walks AST expressions and resolves types
 * from the docs metadata (receiver types, return types) plus an optional lexical
 * [TypeScope] tracking script-local bindings.
 *
 * Used by hover docs and code completion to determine the receiver type in call chains
 * like `Ignitor.sine().lowpass(1000)` and chains rooted at locals like `signal.distort(...)`.
 *
 * Lookup order for identifiers:
 *  1. Lexical [TypeScope] — local `let` / `const` / `export` / arrow-param bindings.
 *  2. Docs registry — globals like `Ignitor`, `Math`.
 *
 * Returns `null` for any expression whose type cannot be inferred — callers must
 * fall back to name-only behavior in that case.
 */
class ExpressionTypeInferrer(private val registry: KlangDocsRegistry) {

    fun inferType(expr: Expression, scope: TypeScope? = null): KlangType? = when (expr) {
        is NumberLiteral -> KlangType("Number")
        is StringLiteral -> KlangType("String")
        is TemplateLiteral -> KlangType("String")
        is BooleanLiteral -> KlangType("Boolean")
        is NullLiteral -> null
        is ArrayLiteral -> KlangType("Array")
        is ObjectLiteral -> KlangType("Object")

        is Identifier -> inferIdentifier(expr, scope)
        is MemberAccess -> inferMemberAccess(expr, scope)
        is CallExpression -> inferCallExpression(expr, scope)

        // Unsupported — graceful degradation
        else -> null
    }

    private fun inferIdentifier(id: Identifier, scope: TypeScope?): KlangType? {
        // 1. Local binding (shadows the registry — even if its type is unknown,
        //    we don't want to fall through to a same-named registered symbol).
        if (scope != null && scope.contains(id.name)) {
            return scope.resolve(id.name)?.type
        }
        // 2. Registry global (e.g. `Ignitor`, `Math`, registered as KlangProperty). Only a top-level property is
        //    the value of the bare name: an owned one (the stdlib's `Katalyst.slot.duck`) is a member elsewhere.
        val symbol = registry.get(id.name) ?: return null
        val prop = symbol.variants.filterIsInstance<KlangProperty>().firstOrNull { it.owner == null }
        return prop?.type
    }

    private fun inferMemberAccess(ma: MemberAccess, scope: TypeScope?): KlangType? {
        val objType = inferType(ma.obj, scope) ?: return null
        // Look up as callable first (method), then as property
        val callable = registry.getCallable(ma.property, objType)
        if (callable != null) {
            return callable.returnType
        }
        val symbol = registry.get(ma.property) ?: return null
        val prop = symbol.variants.filterIsInstance<KlangProperty>()
            .firstOrNull { it.owner?.simpleName == objType.simpleName }
        return prop?.type
    }

    private fun inferCallExpression(call: CallExpression, scope: TypeScope?): KlangType? =
        resolveCallable(call, scope)?.returnType

    /**
     * The registered callable a call expression dispatches to, or null when unknown.
     *
     * Used for the return type (here) and by `AnalyzedAst` to read the declared
     * parameter types, which is how a lambda argument's parameters get typed.
     */
    fun resolveCallable(call: CallExpression, scope: TypeScope?): KlangCallable? {
        return when (val callee = call.callee) {
            is Identifier -> {
                // Calling a local binding (e.g. `let f = ...; f(...)`) short-circuits
                // the registry lookup — we don't know the return type without
                // call-site / function-body inference, but we must NOT resolve via
                // a same-named global like `signal()` from sprudel. A local holding a
                // callable object (`let d = duck; d(1)`) calls the call form of its type.
                if (scope != null && scope.contains(callee.name)) {
                    return scope.resolve(callee.name)?.type?.let { registry.getCallForm(it) }
                }
                // A plain function or a callable object's call form (`Katalyst(...)`, both receiver-less on
                // the symbol of that name), or a value holding a callable object (`Kat(...)`): the call form
                // of its type, the same way the interpreter dispatches it.
                registry.getCallable(callee.name, receiverType = null)
                    ?: inferIdentifier(callee, scope)?.let { registry.getCallForm(it) }
            }

            is MemberAccess -> {
                // Method call: Ignitor.sine(), pattern.gain(0.5), signal.lowpass(...); or a callable
                // object reached through a member (`Foo.Bar(...)`).
                val objType = inferType(callee.obj, scope) ?: return null
                registry.getCallable(callee.property, objType)
                    ?: inferMemberAccess(callee, scope)?.let { registry.getCallForm(it) }
            }

            else -> null
        }
    }
}
