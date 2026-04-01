package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Completion
import io.peekandpoke.klang.codemirror.ext.CompletionContext
import io.peekandpoke.klang.codemirror.ext.CompletionResult
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.intel.ExpressionTypeInferrer
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType
import io.peekandpoke.kraft.utils.jsObject

/**
 * Creates a CodeMirror completion source that suggests DSL symbols from the [EditorDocContext].
 *
 * Returns a function compatible with CodeMirror's `CompletionConfig.override`.
 */
fun dslCompletionSource(docContext: EditorDocContext): (CompletionContext) -> CompletionResult? {
    return { context ->
        completeDsl(context, docContext)
    }
}

private fun completeDsl(context: CompletionContext, docContext: EditorDocContext): CompletionResult? {
    // Match word being typed (letters, digits, underscores)
    val word = context.matchBefore(js("/\\w*/"))
    if (word == null || (word.from == word.to && !context.explicit)) {
        return null
    }

    val from: Int = word.from.unsafeCast<Int>()
    val to: Int = word.to.unsafeCast<Int>()
    val typed = context.state.doc.sliceString(from, to)

    // Skip if inside a string literal
    if (isInsideString(context, from)) {
        return null
    }

    // Detect member access context: is there a '.' immediately before the word?
    val charBefore = if (from > 0) {
        context.state.doc.sliceString(from - 1, from)
    } else {
        ""
    }
    val isMemberAccess = charBefore == "."

    val options = mutableListOf<Completion>()

    if (isMemberAccess) {
        // Try to infer the receiver type from the expression before the dot
        val receiverType = inferReceiverTypeBeforeDot(docContext, from - 1)
        if (receiverType != null) {
            // Show only methods/properties that apply to this receiver type
            val matchingSymbols = docContext.registry.getVariantsForReceiver(receiverType)
            for (symbol in matchingSymbols) {
                if (typed.isNotEmpty() && !symbol.name.startsWith(typed, ignoreCase = true)) {
                    continue
                }
                options.add(symbol.toCompletionForReceiver(receiverType))
            }
        } else {
            // Could not infer receiver type — show all symbols (fallback)
            addAllSymbols(options, docContext.registry.symbols, typed)
        }
    } else {
        // Top-level context: show objects, properties, and top-level functions
        val symbols = docContext.registry.symbols
        for ((name, symbol) in symbols) {
            if (typed.isNotEmpty() && !name.startsWith(typed, ignoreCase = true)) {
                continue
            }
            if (symbol.hasTopLevelVariant()) {
                options.add(symbol.toCompletion())
            }
        }

        // Add alias completions for top-level symbols
        for ((_, symbol) in symbols) {
            if (!symbol.hasTopLevelVariant()) {
                continue
            }
            for (alias in symbol.aliases) {
                if (typed.isNotEmpty() && !alias.startsWith(typed, ignoreCase = true)) {
                    continue
                }
                options.add(symbol.toAliasCompletion(alias))
            }
        }
    }

    // Add import completion if typing an import statement
    val importMatch = checkImportContext(context)
    if (importMatch != null) {
        for (libName in docContext.availableLibraryNames) {
            options.add(jsObject {
                label = libName
                type = "keyword"
                detail = "library"
            })
        }
    }

    if (options.isEmpty()) {
        return null
    }

    return jsObject<CompletionResult> {
        this.from = from
        this.options = options.toTypedArray()
    }
}

/** Try to infer the receiver type of the expression before a dot. */
private fun inferReceiverTypeBeforeDot(docContext: EditorDocContext, dotPos: Int): KlangType? {
    val astIndex = docContext.lastAstIndex ?: return null
    // Look at dotPos - 1: the last character of the expression before the dot.
    // The dot itself didn't exist in the stale AST, so nodeAt(dotPos) misses
    // because the outermost node's exclusive end equals dotPos.
    val node = astIndex.nodeAt(dotPos - 1) ?: return null
    if (node !is Expression) {
        return null
    }
    val inferrer = ExpressionTypeInferrer(docContext.registry)
    return inferrer.inferType(node)
}

/** Add all symbols to the options list (fallback when receiver is unknown). */
private fun addAllSymbols(
    options: MutableList<Completion>,
    symbols: Map<String, KlangSymbol>,
    typed: String,
) {
    for ((name, symbol) in symbols) {
        if (typed.isNotEmpty() && !name.startsWith(typed, ignoreCase = true)) {
            continue
        }
        options.add(symbol.toCompletion())
    }
    for ((_, symbol) in symbols) {
        for (alias in symbol.aliases) {
            if (typed.isNotEmpty() && !alias.startsWith(typed, ignoreCase = true)) {
                continue
            }
            options.add(symbol.toAliasCompletion(alias))
        }
    }
}

/** Check if a symbol has a top-level variant (receiver=null) or is a property (object). */
private fun KlangSymbol.hasTopLevelVariant(): Boolean {
    return variants.any { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver == null
            is KlangProperty -> variant.owner == null
        }
    }
}

/** Completion item showing the receiver-specific variant. */
private fun KlangSymbol.toCompletionForReceiver(receiverType: KlangType): Completion = jsObject {
    label = name
    val matchingVariant = variants.firstOrNull { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver?.simpleName == receiverType.simpleName
            is KlangProperty -> variant.owner?.simpleName == receiverType.simpleName
        }
    }
    type = when (matchingVariant) {
        is KlangCallable -> "function"
        is KlangProperty -> "variable"
        else -> "text"
    }
    val lib = matchingVariant?.library ?: library
    detail = buildString {
        if (lib.isNotEmpty()) {
            append(lib)
        }
        if (isNotEmpty()) {
            append(" · ")
        }
        append(receiverType.simpleName)
    }
    val desc = matchingVariant?.description
    if (!desc.isNullOrBlank()) {
        info = desc.take(200)
    }
}

private fun KlangSymbol.toCompletion(): Completion = jsObject {
    label = name
    // Prefer top-level variant (receiver=null) for display in top-level context
    val topLevelVariant = variants.firstOrNull { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver == null
            is KlangProperty -> variant.owner == null
        }
    }
    val displayVariant = topLevelVariant ?: variants.firstOrNull()
    type = when (displayVariant) {
        is KlangCallable -> "function"
        is KlangProperty -> "variable"
        else -> "text"
    }
    val lib = displayVariant?.library ?: library
    detail = buildString {
        if (lib.isNotEmpty()) {
            append(lib)
        }
        if (category.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" · ")
            }
            append(category)
        }
    }
    val desc = displayVariant?.description
    if (!desc.isNullOrBlank()) {
        info = desc.take(200)
    }
}

private fun KlangSymbol.toAliasCompletion(alias: String): Completion = jsObject {
    label = alias
    val topLevelVariant = variants.firstOrNull { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver == null
            is KlangProperty -> variant.owner == null
        }
    }
    val displayVariant = topLevelVariant ?: variants.firstOrNull()
    type = when (displayVariant) {
        is KlangCallable -> "function"
        is KlangProperty -> "variable"
        else -> "text"
    }
    val lib = displayVariant?.library ?: library
    detail = buildString {
        if (lib.isNotEmpty()) {
            append(lib)
        }
        if (category.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" · ")
            }
            append(category)
        }
        append(" (alias for $name)")
    }
    val desc = displayVariant?.description
    if (!desc.isNullOrBlank()) {
        info = desc.take(200)
    }
}

/**
 * Check if the cursor is inside a string literal by scanning for unescaped quotes.
 * Handles both double-quote ("...") and single-quote ('...') strings.
 */
private fun isInsideString(context: CompletionContext, wordFrom: Int): Boolean {
    val line = context.state.doc.lineAt(wordFrom)
    val prefix = context.state.doc.sliceString(line.from, wordFrom)
    return isInsideStringLiteral(prefix)
}

/**
 * Returns true if the end of [prefix] falls inside an unclosed string literal.
 *
 * Scans for unescaped `"` and `'` — when we encounter an opening quote, everything until
 * the matching closing quote (or end of prefix) is inside a string. Escaped quotes (`\"`, `\'`)
 * are skipped. Handles both double-quote and single-quote strings as used in KlangScript.
 *
 * Shared between [dslCompletionSource] and [dslEditorExtension].
 */
internal fun isInsideStringLiteral(prefix: String): Boolean {
    var inString = false
    var openQuote = ' ' // the quote character that opened the current string
    var i = 0
    while (i < prefix.length) {
        val ch = prefix[i]
        if (inString) {
            if (ch == '\\') {
                i += 2; continue // skip escaped character
            }
            if (ch == openQuote) {
                inString = false // closing quote
            }
        } else {
            if (ch == '"' || ch == '\'') {
                inString = true
                openQuote = ch
            }
        }
        i++
    }
    return inString
}

/**
 * Check if the cursor is in an import statement context (after `from "`).
 */
private fun checkImportContext(context: CompletionContext): Boolean? {
    val line = context.state.doc.lineAt(context.pos)
    val lineText = context.state.doc.sliceString(line.from, context.pos)
    return if (lineText.contains("from") && lineText.contains("\"")) {
        true
    } else {
        null
    }
}
