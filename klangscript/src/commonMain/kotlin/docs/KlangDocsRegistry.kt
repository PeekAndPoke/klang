package io.peekandpoke.klang.script.docs

import io.peekandpoke.klang.script.types.KlangSymbol

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
     * @param doc The symbol to register (overwrites any existing entry with the same name)
     */
    fun register(doc: KlangSymbol) {
        _symbols[doc.name] = doc
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
}
