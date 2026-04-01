package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.intel.ExpressionTypeInferrer
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolRegistry

/**
 * Information about a function argument at a given cursor position.
 *
 * @param functionName  Name of the enclosing function/method call.
 * @param symbol        The resolved KlangSymbol for that function.
 * @param paramIndex    Zero-based index of the argument the cursor is on.
 * @param paramName     Name of the corresponding parameter.
 * @param tools         Resolved ui tools for this param (name -> tool), in declaration order.
 * @param argFrom       Start offset in the source string (inclusive, whitespace-trimmed).
 * @param argTo         End offset in the source string (exclusive, whitespace-trimmed).
 * @param argText       Raw source text of the argument, trimmed of surrounding whitespace.
 */
data class CallArgInfo(
    val functionName: String,
    val symbol: KlangSymbol,
    val paramIndex: Int,
    val paramName: String,
    val tools: List<Pair<String, KlangUiTool>>,
    val argFrom: Int,
    val argTo: Int,
    val argText: String,
)

/**
 * Resolves the parameter for a given argument index, handling vararg params.
 * If argIndex is beyond the param list and the last param is vararg, returns the last param.
 */
private fun resolveParam(callable: KlangCallable, argIndex: Int): KlangParam? {
    return callable.params.getOrNull(argIndex)
        ?: callable.params.lastOrNull()?.takeIf { it.isVararg }
}

/**
 * Finds the function-call argument at [pos] in [source] and returns tool information for it,
 * or null if the cursor is not inside a known function's argument that has `@param-tool` tools.
 *
 * Algorithm:
 * 1. Scan backward from [pos] to find the nearest enclosing `(` (respecting string literals).
 * 2. Look backward from that `(` to identify the function/method name.
 * 3. Look up the name in [docProvider].
 * 4. Scan forward from `(` to find argument boundaries (respecting nesting and strings).
 * 5. Determine which argument [pos] falls in and resolve its `@param-tool` tools.
 */
fun findCallArgAt(
    source: String,
    pos: Int,
    docProvider: (String) -> KlangSymbol?,
): CallArgInfo? {
    if (pos <= 0 || source.isEmpty()) return null

    // -- Step 1: find enclosing `(` by scanning backward --

    var depth = 0
    var openParen = -1
    var i = minOf(pos - 1, source.lastIndex)

    while (i >= 0) {
        when (source[i]) {
            ')' -> depth++
            '(' -> {
                if (depth == 0) {
                    openParen = i; break
                }
                depth--
            }
            // Strings are not tracked backward -- the cursor may be inside a string arg,
            // so we just track paren depth and rely on the forward scan for accuracy.
        }
        i--
    }

    if (openParen < 0) return null

    // -- Step 2: identifier immediately before `(` --

    var j = openParen - 1
    while (j >= 0 && source[j].isWhitespace()) j--
    val nameEnd = j + 1
    while (j >= 0 && (source[j].isLetterOrDigit() || source[j] == '_')) j--
    val nameStart = j + 1
    if (nameStart >= nameEnd) return null
    val functionName = source.substring(nameStart, nameEnd)

    // -- Step 3: resolve symbol --

    val symbol = docProvider(functionName) ?: return null

    // -- Step 4: scan forward to find argument boundaries [from, to) --

    data class ArgBound(val from: Int, val to: Int)

    val argBounds = mutableListOf<ArgBound>()
    var argStart = openParen + 1
    var fwdDepth = 0
    var inString = false
    var stringChar = ' '
    var k = openParen + 1

    while (k < source.length) {
        val ch = source[k]
        when {
            inString -> {
                if (ch == stringChar && (k == 0 || source[k - 1] != '\\')) inString = false
            }

            ch == '"' || ch == '\'' -> {
                inString = true; stringChar = ch
            }

            ch == '(' || ch == '[' || ch == '{' -> fwdDepth++
            ch == ')' || ch == ']' || ch == '}' -> {
                if (fwdDepth == 0) {
                    argBounds.add(ArgBound(argStart, k))
                    break
                }
                fwdDepth--
            }

            ch == ',' && fwdDepth == 0 -> {
                argBounds.add(ArgBound(argStart, k))
                argStart = k + 1
            }
        }
        k++
    }

    if (argBounds.isEmpty()) return null

    // -- Step 5: find which arg contains pos --

    val argIndex = argBounds.indexOfFirst { b -> pos in b.from until b.to }
    if (argIndex < 0) return null

    val bound = argBounds[argIndex]
    val rawSlice = source.substring(bound.from, bound.to)
    val trimmed = rawSlice.trim()
    if (trimmed.isEmpty()) return null
    val leadingSpaces = rawSlice.indexOfFirst { !it.isWhitespace() }
    val argFrom = bound.from + leadingSpaces
    val argTo = argFrom + trimmed.length

    // -- Step 6: match param + resolve tools --

    val callable = symbol.variants.filterIsInstance<KlangCallable>().firstOrNull() ?: return null
    val param = resolveParam(callable, argIndex) ?: return null
    val tools = KlangUiToolRegistry.resolve(param.uitools)
    if (tools.isEmpty()) return null

    return CallArgInfo(
        functionName = functionName,
        symbol = symbol,
        paramIndex = argIndex,
        paramName = param.name,
        tools = tools,
        argFrom = argFrom,
        argTo = argTo,
        argText = trimmed,
    )
}

/**
 * AST-based version of [findCallArgAt]. Uses a pre-built [AstIndex] to look up the deepest
 * AST node at [pos] and walk up to the enclosing CallExpression.
 *
 * Falls back to the text-scanner [findCallArgAt] if [astIndex] is null (parse failed).
 *
 * Advantages over the text scanner:
 * - Correct for nested calls: `foo(bar(1), 2)` correctly resolves inner vs outer
 * - Handles strings with parens: `foo("hello (world)")` doesn't confuse the scanner
 * - Handles multiline code and lambdas correctly
 * - O(log n) lookup after one-time O(n) index build
 */
fun findCallArgAtAst(
    astIndex: AstIndex?,
    source: String,
    pos: Int,
    docProvider: (String) -> KlangSymbol?,
    registry: KlangDocsRegistry? = null,
): CallArgInfo? {
    // Fall back to text scanner if no AST index available
    if (astIndex == null) return findCallArgAt(source, pos, docProvider)

    val result = astIndex.callArgAt(pos) ?: return null
    if (result.argIndex < 0) return null

    val symbol = docProvider(result.functionName) ?: return null

    // Try receiver-aware variant resolution
    val callable = if (registry != null) {
        val callee = result.call.callee
        if (callee is MemberAccess) {
            val inferrer = ExpressionTypeInferrer(registry)
            val receiverType = inferrer.inferType(callee.obj)
            if (receiverType != null) {
                registry.getCallable(result.functionName, receiverType)
            } else {
                symbol.variants.filterIsInstance<KlangCallable>().firstOrNull()
            }
        } else {
            registry.getCallable(result.functionName, receiverType = null)
                ?: symbol.variants.filterIsInstance<KlangCallable>().firstOrNull()
        }
    } else {
        symbol.variants.filterIsInstance<KlangCallable>().firstOrNull()
    } ?: return null
    val param = resolveParam(callable, result.argIndex) ?: return null
    val tools = KlangUiToolRegistry.resolve(param.uitools)
    if (tools.isEmpty()) return null

    // Use cursor node range (the actual node under the cursor, e.g., a StringLiteral)
    // rather than the full argument range (which might include chained method calls)
    val textFrom = result.cursorNodeFrom
    val textTo = result.cursorNodeTo
    val rawSlice = if (textFrom < textTo && textTo <= source.length) {
        source.substring(textFrom, textTo)
    } else {
        ""
    }
    val trimmed = rawSlice.trim()
    if (trimmed.isEmpty()) return null

    val leadingSpaces = rawSlice.indexOfFirst { !it.isWhitespace() }
    val argFrom = textFrom + maxOf(0, leadingSpaces)
    val argTo = argFrom + trimmed.length

    return CallArgInfo(
        functionName = result.functionName,
        symbol = symbol,
        paramIndex = result.argIndex,
        paramName = param.name,
        tools = tools,
        argFrom = argFrom,
        argTo = argTo,
        argText = trimmed,
    )
}
