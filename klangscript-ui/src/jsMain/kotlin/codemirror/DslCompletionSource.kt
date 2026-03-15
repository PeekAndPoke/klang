package io.peekandpoke.klang.ui.codemirror

import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.Completion
import io.peekandpoke.klang.codemirror.ext.CompletionContext
import io.peekandpoke.klang.codemirror.ext.CompletionResult
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol

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
    if (word == null || (word.from == word.to && !context.explicit)) return null

    val from: Int = word.from.unsafeCast<Int>()
    val to: Int = word.to.unsafeCast<Int>()
    val typed = context.state.doc.sliceString(from, to)

    // Skip if inside a string literal
    if (isInsideString(context, from)) return null

    val options = mutableListOf<Completion>()

    // Add DSL symbol completions
    val symbols = docContext.registry.symbols
    for ((name, symbol) in symbols) {
        if (typed.isNotEmpty() && !name.startsWith(typed, ignoreCase = true)) continue
        options.add(symbol.toCompletion())
    }

    // Add alias completions
    for ((_, symbol) in symbols) {
        for (alias in symbol.aliases) {
            if (typed.isNotEmpty() && !alias.startsWith(typed, ignoreCase = true)) continue
            options.add(symbol.toAliasCompletion(alias))
        }
    }

    // Add import completion if typing an import statement
    val importMatch = checkImportContext(context)
    if (importMatch != null) {
        for (libName in docContext.availableLibraryNames) {
            options.add(jsObject<Completion> {
                label = libName
                type = "keyword"
                detail = "library"
            })
        }
    }

    if (options.isEmpty()) return null

    return jsObject<CompletionResult> {
        this.from = from
        this.options = options.toTypedArray()
    }
}

private fun KlangSymbol.toCompletion(): Completion = jsObject {
    label = name
    type = when (variants.firstOrNull()) {
        is KlangCallable -> "function"
        is KlangProperty -> "variable"
        else -> "text"
    }
    detail = category
    val desc = variants.firstOrNull()?.description
    if (!desc.isNullOrBlank()) {
        info = desc.take(200)
    }
}

private fun KlangSymbol.toAliasCompletion(alias: String): Completion = jsObject {
    label = alias
    type = when (variants.firstOrNull()) {
        is KlangCallable -> "function"
        is KlangProperty -> "variable"
        else -> "text"
    }
    detail = "$category (alias for $name)"
    val desc = variants.firstOrNull()?.description
    if (!desc.isNullOrBlank()) {
        info = desc.take(200)
    }
}

/**
 * Check if the cursor is inside a string literal by counting unescaped quotes.
 */
private fun isInsideString(context: CompletionContext, wordFrom: Int): Boolean {
    val line = context.state.doc.lineAt(wordFrom)
    val prefix = context.state.doc.sliceString(line.from, wordFrom)
    var quoteCount = 0
    var i = 0
    while (i < prefix.length) {
        if (prefix[i] == '\\') {
            i += 2; continue
        }
        if (prefix[i] == '"') quoteCount++
        i++
    }
    return quoteCount % 2 != 0
}

/**
 * Check if the cursor is in an import statement context (after `from "`).
 */
private fun checkImportContext(context: CompletionContext): Boolean? {
    val line = context.state.doc.lineAt(context.pos)
    val lineText = context.state.doc.sliceString(line.from, context.pos)
    return if (lineText.contains("from") && lineText.contains("\"")) true else null
}
