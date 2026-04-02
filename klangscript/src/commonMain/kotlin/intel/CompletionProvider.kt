package io.peekandpoke.klang.script.intel

import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangDecl
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

private const val MAX_DESCRIPTION_LENGTH = 200

/**
 * A code completion suggestion, independent of any editor framework.
 */
data class CompletionSuggestion(
    val name: String,
    val kind: Kind,
    val detail: String,
    val description: String,
    val isAlias: Boolean,
    val aliasFor: String?,
) {
    enum class Kind { FUNCTION, PROPERTY, KEYWORD }
}

/**
 * Provides code completion suggestions based on a [KlangDocsRegistry].
 *
 * This is pure Kotlin logic with no editor dependencies — testable on JVM.
 * The editor adapter (e.g., CodeMirror) converts these suggestions to its own format.
 */
class CompletionProvider(private val registry: KlangDocsRegistry) {

    /**
     * Top-level completions: objects, properties, and top-level functions.
     * Filtered by [prefix] (case-insensitive). Pass empty string for all.
     */
    fun topLevelCompletions(prefix: String): List<CompletionSuggestion> {
        val suggestions = mutableListOf<CompletionSuggestion>()

        for ((name, symbol) in registry.symbols) {
            if (!symbol.hasTopLevelVariant()) {
                continue
            }
            if (prefix.isNotEmpty() && !name.startsWith(prefix, ignoreCase = true)) {
                continue
            }
            suggestions.add(symbol.toTopLevelSuggestion())
        }

        // Add alias completions for top-level symbols
        for ((_, symbol) in registry.symbols) {
            if (!symbol.hasTopLevelVariant()) {
                continue
            }
            for (alias in symbol.aliases) {
                if (prefix.isNotEmpty() && !alias.startsWith(prefix, ignoreCase = true)) {
                    continue
                }
                suggestions.add(symbol.toAliasSuggestion(alias))
            }
        }

        return suggestions
    }

    /**
     * Member completions: methods/properties available on the given [receiverType].
     * Filtered by [prefix] (case-insensitive). Pass empty string for all.
     */
    fun memberCompletions(receiverType: KlangType, prefix: String): List<CompletionSuggestion> {
        val matchingSymbols = registry.getVariantsForReceiver(receiverType)
        val suggestions = mutableListOf<CompletionSuggestion>()

        for (symbol in matchingSymbols) {
            if (prefix.isNotEmpty() && !symbol.name.startsWith(prefix, ignoreCase = true)) {
                continue
            }
            suggestions.add(symbol.toMemberSuggestion(receiverType))
        }

        return suggestions
    }

    /**
     * Import completions: available library names.
     */
    fun importCompletions(availableLibraries: Set<String>): List<CompletionSuggestion> {
        return availableLibraries.map { libName ->
            CompletionSuggestion(
                name = libName,
                kind = CompletionSuggestion.Kind.KEYWORD,
                detail = "library",
                description = "",
                isAlias = false,
                aliasFor = null,
            )
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

private fun KlangSymbol.hasTopLevelVariant(): Boolean {
    return firstTopLevelVariant() != null
}

private fun KlangSymbol.firstTopLevelVariant(): KlangDecl? {
    return variants.firstOrNull { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver == null
            is KlangProperty -> variant.owner == null
        }
    }
}

private fun KlangSymbol.toTopLevelSuggestion(): CompletionSuggestion {
    val displayVariant = firstTopLevelVariant() ?: variants.firstOrNull()

    return CompletionSuggestion(
        name = name,
        kind = displayVariant.toKind(),
        detail = buildDetail(displayVariant?.library ?: library, category),
        description = displayVariant?.description?.take(MAX_DESCRIPTION_LENGTH) ?: "",
        isAlias = false,
        aliasFor = null,
    )
}

private fun KlangSymbol.toAliasSuggestion(alias: String): CompletionSuggestion {
    val displayVariant = firstTopLevelVariant() ?: variants.firstOrNull()

    return CompletionSuggestion(
        name = alias,
        kind = displayVariant.toKind(),
        detail = buildDetail(displayVariant?.library ?: library, category, "(alias for $name)"),
        description = displayVariant?.description?.take(MAX_DESCRIPTION_LENGTH) ?: "",
        isAlias = true,
        aliasFor = name,
    )
}

private fun KlangSymbol.toMemberSuggestion(receiverType: KlangType): CompletionSuggestion {
    val matchingVariant = variants.firstOrNull { variant ->
        when (variant) {
            is KlangCallable -> variant.receiver?.simpleName == receiverType.simpleName
            is KlangProperty -> variant.owner?.simpleName == receiverType.simpleName
        }
    }

    return CompletionSuggestion(
        name = name,
        kind = matchingVariant.toKind(),
        detail = buildDetail(matchingVariant?.library ?: library, receiverType.simpleName),
        description = matchingVariant?.description?.take(MAX_DESCRIPTION_LENGTH) ?: "",
        isAlias = false,
        aliasFor = null,
    )
}

private fun KlangDecl?.toKind(): CompletionSuggestion.Kind {
    return when (this) {
        is KlangCallable -> CompletionSuggestion.Kind.FUNCTION
        is KlangProperty -> CompletionSuggestion.Kind.PROPERTY
        else -> CompletionSuggestion.Kind.PROPERTY
    }
}

private fun buildDetail(vararg parts: String): String {
    return parts.filter { it.isNotEmpty() }.joinToString(" · ")
}
