package io.peekandpoke.klang.script.docs

import io.peekandpoke.klang.script.types.KlangSymbol

class KlangDocsRegistry {

    companion object {
        val global = KlangDocsRegistry()
    }

    private val _symbols = mutableMapOf<String, KlangSymbol>()

    val symbols: Map<String, KlangSymbol>
        get() = _symbols.toMap()

    fun register(doc: KlangSymbol) {
        _symbols[doc.name] = doc
    }

    fun registerAll(docs: List<KlangSymbol>) {
        docs.forEach { register(it) }
    }

    fun registerAll(docs: Map<String, KlangSymbol>) {
        docs.values.forEach { register(it) }
    }

    fun clear() {
        _symbols.clear()
    }

    fun get(name: String): KlangSymbol? =
        _symbols[name]

    val symbolNames: List<String>
        get() = _symbols.keys.sorted()

    val categories: List<String>
        get() = _symbols.values.map { it.category }.distinct().sorted()

    val libraries: List<String>
        get() = _symbols.values.map { it.library }.filter { it.isNotEmpty() }.distinct().sorted()

    fun getByCategory(category: String): List<KlangSymbol> =
        _symbols.values.filter { it.category == category }.sortedBy { it.name }

    fun getByLibrary(library: String): List<KlangSymbol> =
        _symbols.values.filter { it.library == library }.sortedBy { it.name }

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
