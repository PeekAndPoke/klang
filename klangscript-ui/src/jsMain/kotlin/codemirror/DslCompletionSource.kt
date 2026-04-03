package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Completion
import io.peekandpoke.klang.codemirror.ext.CompletionContext
import io.peekandpoke.klang.codemirror.ext.CompletionResult
import io.peekandpoke.klang.script.intel.CompletionProvider
import io.peekandpoke.klang.script.intel.CompletionSuggestion
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

    // Force immediate re-parse so the analysis matches the current editor text.
    // The debounced onCodeChanged may not have fired yet when completion triggers.
    val currentCode = context.state.doc.toString()
    docContext.processCodeImmediate(currentCode)

    val provider = CompletionProvider(docContext.registry)
    val options = mutableListOf<Completion>()

    // Detect member access context: is there a '.' immediately before the word?
    val charBefore = if (from > 0) {
        context.state.doc.sliceString(from - 1, from)
    } else {
        ""
    }
    val isMemberAccess = charBefore == "."

    if (isMemberAccess) {
        val receiverType = inferReceiverTypeBeforeDot(docContext, from - 1)
        if (receiverType != null) {
            val suggestions = provider.memberCompletions(receiverType, typed)
            suggestions.mapTo(options) { it.toCodeMirrorCompletion() }
        }
        // If receiver type can't be inferred (stale AST, parse error), show nothing.
        // Showing top-level symbols after a dot is always wrong.
    } else {
        val suggestions = provider.topLevelCompletions(typed)
        suggestions.mapTo(options) { it.toCodeMirrorCompletion() }
    }

    // Add import completion if typing an import statement
    if (isImportContext(context)) {
        val importSuggestions = provider.importCompletions(docContext.availableLibraryNames)
        importSuggestions.mapTo(options) { it.toCodeMirrorCompletion() }
    }

    if (options.isEmpty()) {
        return null
    }

    return jsObject<CompletionResult> {
        this.from = from
        this.options = options.toTypedArray()
    }
}

/** Try to infer the receiver type of the expression before a dot using the cached [AnalyzedAst]. */
private fun inferReceiverTypeBeforeDot(docContext: EditorDocContext, dotPos: Int): KlangType? {
    val analysis = docContext.lastAnalysis ?: return null
    return analysis.getExpressionTypeEndingAt(dotPos - 1)
}

/** Convert a [CompletionSuggestion] to a CodeMirror [Completion] JS object. */
private fun CompletionSuggestion.toCodeMirrorCompletion(): Completion = jsObject {
    label = name
    type = when (kind) {
        CompletionSuggestion.Kind.FUNCTION -> "function"
        CompletionSuggestion.Kind.PROPERTY -> "variable"
        CompletionSuggestion.Kind.KEYWORD -> "keyword"
    }
    val d = this@toCodeMirrorCompletion.detail
    if (d.isNotEmpty()) {
        this.detail = d
    }
    if (description.isNotEmpty()) {
        info = description
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
    var openQuote = "" // the quote string that opened the current string
    var i = 0
    while (i < prefix.length) {
        val ch = prefix[i].toString()
        if (inString) {
            if (ch == "\\") {
                i += 2
                continue // skip escaped character
            }
            if (ch == openQuote) {
                inString = false // closing quote
            }
        } else {
            if (ch == "\"" || ch == "'") {
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
private fun isImportContext(context: CompletionContext): Boolean {
    val line = context.state.doc.lineAt(context.pos)
    val lineText = context.state.doc.sliceString(line.from, context.pos)
    return lineText.contains("from") && lineText.contains("\"")
}
