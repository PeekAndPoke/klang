/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.CallExpressionAtResult
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.callableForReceiver
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * The callable variant, and its parameter, that one argument of a call binds to.
 *
 * @param wholeCall False when the variant was chosen without a receiver type and another variant
 *   of the same name binds this argument to a parameter of a different name (the untyped
 *   `o => o.tremolo(5, 0.3)`: sprudel's `depth`, the Ignitor's `rate`). The call may then belong to
 *   that other variant, so a tool must edit this one argument only and never rewrite the whole
 *   argument list with [callable]'s parameter names.
 */
data class ArgumentBinding(
    val callable: KlangCallable,
    val param: KlangParam,
    val wholeCall: Boolean = true,
)

/** What is known about the receiver of a call, for [bindArgument]. */
sealed interface CallReceiver {
    /** A member call on a receiver whose type the analysis knows. */
    data class Typed(val type: KlangType) : CallReceiver

    /** A member call on a receiver of unknown type, e.g. the parameter `x` of `x => x.body(...)`. */
    data object Untyped : CallReceiver

    /** A plain call, `body(...)`. */
    data object TopLevel : CallReceiver
}

/**
 * The one resolution of "which parameter does this argument bind to", as the editor's param tools
 * (`@param-tool`) need it. Both the editor's AST path ([argumentAt]) and its text-scanner fallback
 * call this.
 *
 * The variant: on a [CallReceiver.Typed] receiver the receiver-matched one (null when none
 * matches, never a guess); on an [CallReceiver.Untyped] one [KlangSymbol.callableForArgument],
 * which prefers the variant whose parameter declares tools; on a [CallReceiver.TopLevel] call the
 * top-level variant, else the same untyped rule. The parameter: [KlangCallable.paramForArgument].
 *
 * @param argIndex     The argument's position in the call.
 * @param argName      The argument's name when written `name = value`, else null.
 * @param functionArgs Per argument of the call, whether it is a function literal.
 */
fun KlangSymbol.bindArgument(
    receiver: CallReceiver,
    argIndex: Int,
    argName: String?,
    functionArgs: List<Boolean>,
): ArgumentBinding? {
    val matched = when (receiver) {
        is CallReceiver.Typed -> callableForReceiver(receiver.type) ?: return null
        CallReceiver.Untyped -> null
        CallReceiver.TopLevel -> callableForReceiver(null)
    }

    val callable = matched ?: callableForArgument(argIndex, argName, functionArgs) ?: return null
    val param = callable.paramForArgument(argIndex, argName, functionArgs) ?: return null

    // Chosen by the untyped rule: is the choice ambiguous for this argument?
    val ambiguous = matched == null && variants.filterIsInstance<KlangCallable>().any { other ->
        val otherName = other.paramForArgument(argIndex, argName, functionArgs)?.name

        otherName != null && otherName != param.name
    }

    return ArgumentBinding(callable = callable, param = param, wholeCall = !ambiguous)
}

/** A call argument found at a source position, with the symbol and the binding it resolved to. */
data class ArgumentAt(
    val site: CallExpressionAtResult,
    val symbol: KlangSymbol,
    val binding: ArgumentBinding,
)

/**
 * The call argument at [pos] and what it binds to ([bindArgument]), or null when [pos] is not in
 * a call argument or nothing binds. [symbolLookup] finds the called name's symbol, by default in
 * this analysis's registry.
 */
fun AnalyzedAst.argumentAt(
    pos: Int,
    symbolLookup: (String) -> KlangSymbol? = { registry.get(it) },
): ArgumentAt? {
    val site = astIndex.callArgAt(pos) ?: return null

    if (site.argIndex < 0) {
        return null
    }

    val symbol = symbolLookup(site.functionName) ?: return null
    val callee = site.call.callee

    val receiver = if (callee is MemberAccess) {
        typeOf(callee.obj)?.let { CallReceiver.Typed(it) } ?: CallReceiver.Untyped
    } else {
        CallReceiver.TopLevel
    }

    val functionArgs = site.call.arguments.map { it.value is ArrowFunction }
    val binding = symbol.bindArgument(receiver, site.argIndex, site.argName, functionArgs) ?: return null

    return ArgumentAt(site = site, symbol = symbol, binding = binding)
}
