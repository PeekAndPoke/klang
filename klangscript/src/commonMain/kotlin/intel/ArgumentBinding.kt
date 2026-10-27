/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.ast.Argument
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.AstNode
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.CallExpressionAtResult
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.Identifier
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
 * which prefers the variant whose parameter declares tools (a call form is never a member, so never
 * a candidate there); on a [CallReceiver.TopLevel] call the top-level variant, else the same untyped
 * rule. A callable object's call form binds to its own parameters, except where a method has the very
 * same parameter list and declares tools for the argument: a field accessor's or compound's call form
 * (`pan(0.7)`, `lpf(800)`) mirrors its pattern method, which carries the tools; the signals' and
 * `Katalyst`'s call forms have lists of their own. The parameter: [KlangCallable.paramForArgument].
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
        CallReceiver.TopLevel -> callableForReceiver(null)?.let { top ->
            if (top == callForm) toolTwinOf(top, argIndex, argName, functionArgs) ?: top else top
        }
    }

    val callable = matched ?: callableForArgument(argIndex, argName, functionArgs) ?: return null
    val param = callable.paramForArgument(argIndex, argName, functionArgs) ?: return null

    // Chosen by the untyped rule: is the choice ambiguous for this argument? (The call form is no member.)
    val ownCallForm = callForm
    val ambiguous = matched == null && variants.filterIsInstance<KlangCallable>().filter { it != ownCallForm }.any { other ->
        val otherName = other.paramForArgument(argIndex, argName, functionArgs)?.name

        otherName != null && otherName != param.name
    }

    return ArgumentBinding(callable = callable, param = param, wholeCall = !ambiguous)
}

/**
 * For a callable object's [callForm], the method with the very same parameter list (by name) that declares
 * tools for this argument: `SprudelPattern.pan(amount)` for `pan(amount)`. Null when no method mirrors it.
 */
private fun KlangSymbol.toolTwinOf(
    callForm: KlangCallable,
    argIndex: Int,
    argName: String?,
    functionArgs: List<Boolean>,
): KlangCallable? {
    val names = callForm.params.map { it.name }

    return variants.filterIsInstance<KlangCallable>().firstOrNull { other ->
        other != callForm &&
                other.params.map { it.name } == names &&
                other.paramForArgument(argIndex, argName, functionArgs)?.uitools?.isNotEmpty() == true
    }
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
 *
 * A local shadows a same-named global here as everywhere in the analysis: `const gain = (a) => a; gain(0.5)` calls
 * the local, which has no tools. A local holding a callable object (`let d = duck; d(0.5)`) binds through the
 * object's symbol, the way the call dispatches, and so does a second name of the object (`lowpass(800)` binds
 * through `lpf`).
 */
fun AnalyzedAst.argumentAt(
    pos: Int,
    symbolLookup: (String) -> KlangSymbol? = { registry.get(it) },
): ArgumentAt? {
    val site = astIndex.callArgAt(pos) ?: return null

    if (site.argIndex < 0) {
        return null
    }

    val (symbol, receiver) = calledSymbol(call = site.call, functionName = site.functionName, symbolLookup = symbolLookup)
        ?: return null

    val functionArgs = site.call.arguments.map { it.value is ArrowFunction }
    val binding = symbol.bindArgument(receiver, site.argIndex, site.argName, functionArgs) ?: return null

    return ArgumentAt(site = site, symbol = symbol, binding = binding)
}

/**
 * The symbol a call dispatches to, and what is known of its receiver. A local shadows a same-named global; a local
 * holding a callable object binds through the object's symbol, and so does a second name of the object.
 */
private fun AnalyzedAst.calledSymbol(
    call: CallExpression,
    functionName: String,
    symbolLookup: (String) -> KlangSymbol?,
): Pair<KlangSymbol, CallReceiver>? {
    val callee = call.callee
    val local = (callee as? Identifier)?.let { localBindingOf(it) }

    val symbol = if (local != null) {
        val callForm = local.type?.let { registry.getCallForm(it) } ?: return null

        symbolLookup(callForm.name) ?: return null
    } else {
        val named = symbolLookup(functionName) ?: return null

        // A second name of a callable object (`lowpass(800)`, `vel(0.5)`): no top-level callable of its own, so the
        // call dispatches to the object's call form, and the object's symbol carries the tools
        val aliasOf = if (callee is Identifier && named.callableForReceiver(null) == null) registry.callFormFor(named) else null

        aliasOf?.let { symbolLookup(it.name) } ?: named
    }

    val receiver = if (callee is MemberAccess) {
        typeOf(callee.obj)?.let { CallReceiver.Typed(it) } ?: CallReceiver.Untyped
    } else {
        CallReceiver.TopLevel
    }

    return symbol to receiver
}

/**
 * A call around a source position whose arguments have tools, for the editor's Sprudel tool (KLANG-00374): the
 * argument its tools edit and what that binds to.
 *
 * @param argIndex The argument the tools edit; -1 when the call has no argument yet and the tools would write its
 *   first one.
 * @param argName That argument's name when it is written `name = value`.
 * @param atCursor Whether the cursor is on that argument.
 * @param editRange The literal the tools read and write, as source offsets: the argument's value, or the literal its
 *   value starts with (`"c e"` in `"c e".fast(2)`); null for a call without arguments.
 * @param parentheses The call's opening parenthesis and its closing one, as source offsets.
 */
data class ToolCallAt(
    val call: CallExpression,
    val functionName: String,
    val symbol: KlangSymbol,
    val binding: ArgumentBinding,
    val argIndex: Int,
    val argName: String?,
    val atCursor: Boolean,
    val editRange: IntRange?,
    val parentheses: IntRange,
)

/**
 * The innermost call whose parentheses hold [pos] and whose arguments have tools (the maintainer, 2026-10-27:
 * "Whenever the cursor is inside the parenteses if a function that supports tools we should so the tool"), or null.
 * Inside the parentheses: from just after the opening one up to the closing one, so between arguments and in an empty
 * call too; on the called name or after `)` is outside. The argument, among those with tools and a literal a tool can
 * edit (a string or a number, as the value or at the head of its chain): the one at the cursor; else the call's first;
 * else, for a call without arguments, its first parameter. A literal inside a nested call (`4` in
 * `gain(sine.slow(4))`) is never taken for the outer call's parameter. A call with nothing to edit gives way to the
 * call around it.
 */
fun AnalyzedAst.toolCallAt(
    pos: Int,
    symbolLookup: (String) -> KlangSymbol? = { registry.get(it) },
): ToolCallAt? {
    // From the node at the cursor; in space or a comment no node may cover it (an empty call's `( )`), so also from
    // the last character of code before it
    val before = CallText.lastCodeBefore(source = source, pos = pos)

    val starts = listOfNotNull(astIndex.nodeAt(pos), astIndex.nodeAt(before).takeIf { before >= 0 })

    // A node's range is a guess at a call's end (space before its `)` may lie outside), so each start may stop at an
    // outer call first: of what the starts find, the innermost call wins
    return starts.mapNotNull { start ->
        var node: AstNode = start
        var found: ToolCallAt? = null

        while (found == null) {
            if (node is CallExpression) {
                found = toolsOfCall(call = node, pos = pos, symbolLookup = symbolLookup)
            }

            node = astIndex.parentOf(node) ?: break
        }

        found
    }.maxByOrNull { it.parentheses.first }
}

/** [toolCallAt] for one call: null when [pos] is not in its parentheses or none of its arguments can be edited. */
private fun AnalyzedAst.toolsOfCall(
    call: CallExpression,
    pos: Int,
    symbolLookup: (String) -> KlangSymbol?,
): ToolCallAt? {
    val calleeEnd = astIndex.offsetOf(call.callee)?.last ?: return null
    val parens = CallText.parentheses(source = source, from = calleeEnd + 1) ?: return null

    // The cursor from just after the opening parenthesis to just before the closing one
    if (pos <= parens.first || pos > parens.last) {
        return null
    }

    val functionName = when (val callee = call.callee) {
        is Identifier -> callee.name
        is MemberAccess -> callee.property
        else -> return null
    }

    val (symbol, receiver) = calledSymbol(call = call, functionName = functionName, symbolLookup = symbolLookup) ?: return null
    val functionArgs = call.arguments.map { it.value is ArrowFunction }

    fun bind(index: Int): ArgumentBinding? {
        val name = (call.arguments.getOrNull(index) as? Argument.Named)?.name

        return symbol.bindArgument(receiver, index, name, functionArgs)?.takeIf { it.param.uitools.isNotEmpty() }
    }

    fun found(index: Int, binding: ArgumentBinding, atCursor: Boolean, editRange: IntRange?) = ToolCallAt(
        call = call,
        functionName = functionName,
        symbol = symbol,
        binding = binding,
        argIndex = index,
        argName = (call.arguments.getOrNull(index) as? Argument.Named)?.name,
        atCursor = atCursor,
        editRange = editRange,
        parentheses = parens,
    )

    // The argument at the cursor: on its value, or just after it
    val atIndex = call.arguments.indexOfFirst { argument ->
        astIndex.offsetOf(argument.value)?.let { range -> pos in range.first..range.last + 1 } == true
    }

    // The literal as the text has it, the way the follower finds it again at each commit
    fun literal(index: Int, binding: ArgumentBinding): IntRange? =
        CallText.literalOf(source = source, parentheses = parens, name = binding.param.name, index = index)

    if (atIndex >= 0) {
        val binding = bind(atIndex)
        val literal = binding?.let { literal(index = atIndex, binding = it) }

        if (binding != null && literal != null) {
            return found(index = atIndex, binding = binding, atCursor = true, editRange = literal)
        }
    }

    for (index in call.arguments.indices) {
        val binding = bind(index) ?: continue
        val literal = literal(index = index, binding = binding) ?: continue

        return found(index = index, binding = binding, atCursor = false, editRange = literal)
    }

    if (call.arguments.isEmpty()) {
        bind(0)?.let { return found(index = -1, binding = it, atCursor = false, editRange = null) }
    }

    return null
}
