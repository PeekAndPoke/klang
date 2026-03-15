package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.script.types.KlangCallable
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
 * @param tools         Resolved ui tools for this param (name → tool), in declaration order.
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

    // ── Step 1: find enclosing `(` by scanning backward ─────────────────────

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
            // Strings are not tracked backward — the cursor may be inside a string arg,
            // so we just track paren depth and rely on the forward scan for accuracy.
        }
        i--
    }

    if (openParen < 0) return null

    // ── Step 2: identifier immediately before `(` ────────────────────────────

    var j = openParen - 1
    while (j >= 0 && source[j].isWhitespace()) j--
    val nameEnd = j + 1
    while (j >= 0 && (source[j].isLetterOrDigit() || source[j] == '_')) j--
    val nameStart = j + 1
    if (nameStart >= nameEnd) return null
    val functionName = source.substring(nameStart, nameEnd)

    // ── Step 3: resolve symbol ────────────────────────────────────────────────

    val symbol = docProvider(functionName) ?: return null

    // ── Step 4: scan forward to find argument boundaries [from, to) ──────────

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

    // ── Step 5: find which arg contains pos ──────────────────────────────────

    val argIndex = argBounds.indexOfFirst { b -> pos in b.from until b.to }
    if (argIndex < 0) return null

    val bound = argBounds[argIndex]
    val rawSlice = source.substring(bound.from, bound.to)
    val trimmed = rawSlice.trim()
    if (trimmed.isEmpty()) return null
    val leadingSpaces = rawSlice.indexOfFirst { !it.isWhitespace() }
    val argFrom = bound.from + leadingSpaces
    val argTo = argFrom + trimmed.length

    // ── Step 6: match param + resolve tools ──────────────────────────────────

    val callable = symbol.variants.filterIsInstance<KlangCallable>().firstOrNull() ?: return null
    val param = callable.params.getOrNull(argIndex) ?: return null
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
