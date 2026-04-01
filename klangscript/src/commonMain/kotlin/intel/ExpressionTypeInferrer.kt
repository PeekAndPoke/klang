package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.*
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangType

/**
 * Lightweight static type inferrer that walks AST expressions and resolves types
 * from the docs metadata (receiver types, return types).
 *
 * Used by hover docs and code completion to determine the receiver type in call chains
 * like `Osc.sine().lowpass(1000)`.
 *
 * Returns `null` for any expression whose type cannot be inferred — callers must
 * fall back to name-only behavior in that case.
 */
class ExpressionTypeInferrer(private val registry: KlangDocsRegistry) {

    fun inferType(expr: Expression): KlangType? = when (expr) {
        is NumberLiteral -> KlangType("Number")
        is StringLiteral -> KlangType("String")
        is TemplateLiteral -> KlangType("String")
        is BooleanLiteral -> KlangType("Boolean")
        is NullLiteral -> null
        is ArrayLiteral -> KlangType("Array")
        is ObjectLiteral -> KlangType("Object")

        is Identifier -> inferIdentifier(expr)
        is MemberAccess -> inferMemberAccess(expr)
        is CallExpression -> inferCallExpression(expr)

        // Unsupported — graceful degradation
        else -> null
    }

    private fun inferIdentifier(id: Identifier): KlangType? {
        val symbol = registry.get(id.name) ?: return null
        val prop = symbol.variants.filterIsInstance<KlangProperty>().firstOrNull()
        if (prop != null) {
            return prop.type
        }
        return null
    }

    private fun inferMemberAccess(ma: MemberAccess): KlangType? {
        val objType = inferType(ma.obj) ?: return null
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

    private fun inferCallExpression(call: CallExpression): KlangType? {
        return when (val callee = call.callee) {
            is Identifier -> {
                // Top-level call: note("c3"), print("hello")
                val callable = registry.getCallable(callee.name, receiverType = null)
                callable?.returnType
            }

            is MemberAccess -> {
                // Method call: Osc.sine(), pattern.gain(0.5)
                val objType = inferType(callee.obj) ?: return null
                val callable = registry.getCallable(callee.property, objType)
                callable?.returnType
            }

            else -> null
        }
    }
}
