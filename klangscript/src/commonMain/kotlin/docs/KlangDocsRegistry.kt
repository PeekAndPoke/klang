package io.peekandpoke.klang.script.docs

import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Registry for KlangScript symbol documentation.
 *
 * Stores and queries [KlangSymbol] entries by name, category, library, or free-text search.
 */
class KlangDocsRegistry {

    companion object {
        /** Global shared docs registry. */
        val global = KlangDocsRegistry()
    }

    private val _symbols = mutableMapOf<String, KlangSymbol>()

    /** Snapshot of all registered symbols keyed by name. */
    val symbols: Map<String, KlangSymbol>
        get() = _symbols.toMap()

    /**
     * Register a single symbol.
     *
     * When a symbol with the same name already exists, variants are merged so that
     * symbols from multiple libraries (e.g. stdlib and sprudel) coexist.
     */
    fun register(doc: KlangSymbol) {
        val existing = _symbols[doc.name]
        if (existing != null) {
            // Merge variants, deduplicating by (name, receiver, library) so that
            // re-registration from a second KSP processor doesn't produce
            // duplicate entries in the docs popup — while keeping legitimate
            // cross-library variants (e.g. "adsr" from stdlib + sprudel).
            val merged = (existing.variants + doc.variants).distinctBy { variant ->
                when (variant) {
                    is KlangCallable -> Triple(variant.name, variant.receiver?.simpleName, variant.library)
                    is KlangProperty -> Triple(variant.name, variant.owner?.simpleName, variant.library)
                }
            }
            _symbols[doc.name] = existing.copy(
                variants = merged,
                tags = (existing.tags + doc.tags).distinct(),
                aliases = (existing.aliases + doc.aliases).distinct(),
            )
        } else {
            _symbols[doc.name] = doc
        }
    }

    /**
     * Register multiple symbols from a list.
     *
     * @param docs Symbols to register
     */
    fun registerAll(docs: List<KlangSymbol>) {
        docs.forEach { register(it) }
    }

    /**
     * Register multiple symbols from a map.
     *
     * @param docs Map of symbol names to symbols
     */
    fun registerAll(docs: Map<String, KlangSymbol>) {
        docs.values.forEach { register(it) }
    }

    /** Remove all registered symbols. */
    fun clear() {
        _symbols.clear()
    }

    /** Create a snapshot copy of this registry. Mutations to the original do not affect the copy. */
    fun snapshot(): KlangDocsRegistry = KlangDocsRegistry().also { copy ->
        copy._symbols.putAll(_symbols)
    }

    /**
     * Look up a symbol by name.
     *
     * @param name The symbol name
     * @return The symbol, or null if not found
     */
    fun get(name: String): KlangSymbol? =
        _symbols[name]

    /** Sorted list of all registered symbol names. */
    val symbolNames: List<String>
        get() = _symbols.keys.sorted()

    /** Sorted distinct list of all categories present in the registry. */
    val categories: List<String>
        get() = _symbols.values.map { it.category }.distinct().sorted()

    /** Sorted distinct list of all non-empty library names present in the registry. */
    val libraries: List<String>
        get() = _symbols.values.map { it.library }.filter { it.isNotEmpty() }.distinct().sorted()

    /**
     * Get all symbols belonging to a category, sorted by name.
     *
     * @param category The category to filter by
     * @return Matching symbols sorted by name
     */
    fun getByCategory(category: String): List<KlangSymbol> =
        _symbols.values.filter { it.category == category }.sortedBy { it.name }

    /**
     * Get all symbols belonging to a library, sorted by name.
     *
     * @param library The library name to filter by
     * @return Matching symbols sorted by name
     */
    fun getByLibrary(library: String): List<KlangSymbol> =
        _symbols.values.filter { it.library == library }.sortedBy { it.name }

    /**
     * Search symbols by free-text query.
     *
     * Matches against name, aliases, tags, category, and library (case-insensitive).
     *
     * @param query The search query
     * @return Matching symbols sorted by name
     */
    fun search(query: String): List<KlangSymbol> {
        val lowerQuery = query.lowercase()
        return _symbols.values.filter { symbol ->
            symbol.name.lowercase().contains(lowerQuery) ||
                    symbol.aliases.any { it.lowercase().contains(lowerQuery) } ||
                    symbol.tags.any { it.lowercase().contains(lowerQuery) } ||
                    symbol.category.lowercase().contains(lowerQuery) ||
                    symbol.library.lowercase().contains(lowerQuery)
        }.sortedBy { it.name }
    }

    /**
     * Find a [KlangCallable] variant matching the given receiver type.
     *
     * @param name Symbol name
     * @param receiverType The receiver type to match, or null for top-level functions
     * @return The matching callable, or null
     */
    fun getCallable(name: String, receiverType: KlangType?): KlangCallable? {
        val symbol = _symbols[name] ?: return null
        return symbol.variants
            .filterIsInstance<KlangCallable>()
            .firstOrNull { it.receiver?.simpleName == receiverType?.simpleName }
    }

    /**
     * Get all symbols that have at least one variant with the given receiver type.
     * Used for code completion after `.` — returns all methods available on a type.
     *
     * @param receiverType The receiver type to filter by
     * @return Matching symbols sorted by name
     */
    fun getVariantsForReceiver(receiverType: KlangType): List<KlangSymbol> {
        return _symbols.values.filter { symbol ->
            symbol.variants.any { variant ->
                when (variant) {
                    is KlangCallable -> variant.receiver?.simpleName == receiverType.simpleName
                    is KlangProperty -> variant.owner?.simpleName == receiverType.simpleName
                }
            }
        }.sortedBy { it.name }
    }

    /**
     * Look up a symbol and filter its variants to those matching the receiver type.
     * Used for hover docs when the receiver context is known.
     *
     * Falls back to the full symbol (all variants) when no variants match.
     *
     * @param name Symbol name
     * @param receiverType The receiver type to filter by, or null to return all variants
     * @return The filtered symbol, or null if the name is not found
     */
    fun getSymbolWithReceiver(name: String, receiverType: KlangType?): KlangSymbol? {
        val symbol = _symbols[name] ?: return null
        if (receiverType == null) return symbol
        val filtered = symbol.variants.filter { variant ->
            when (variant) {
                is KlangCallable -> variant.receiver?.simpleName == receiverType.simpleName
                is KlangProperty -> variant.owner?.simpleName == receiverType.simpleName
            }
        }
        if (filtered.isEmpty()) return symbol // fallback: show all
        val variantLibrary = filtered.firstOrNull()?.library ?: symbol.library
        return symbol.copy(variants = filtered, library = variantLibrary)
    }
}
