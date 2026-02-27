package io.peekandpoke.klang.script.docs

import io.peekandpoke.klang.script.types.KlangFun

class KlangDocsRegistry {

    companion object {
        val global = KlangDocsRegistry()
    }

    private val _functions = mutableMapOf<String, KlangFun>()

    val functions: Map<String, KlangFun>
        get() = _functions.toMap()

    fun register(doc: KlangFun) {
        _functions[doc.name] = doc
    }

    fun registerAll(docs: List<KlangFun>) {
        docs.forEach { register(it) }
    }

    fun registerAll(docs: Map<String, KlangFun>) {
        docs.values.forEach { register(it) }
    }

    fun clear() {
        _functions.clear()
    }

    fun get(functionName: String): KlangFun? =
        _functions[functionName]

    val functionNames: List<String>
        get() = _functions.keys.sorted()

    val categories: List<String>
        get() = _functions.values.map { it.category }.distinct().sorted()

    val libraries: List<String>
        get() = _functions.values.map { it.library }.filter { it.isNotEmpty() }.distinct().sorted()

    fun getFunctionsByCategory(category: String): List<KlangFun> =
        _functions.values.filter { it.category == category }.sortedBy { it.name }

    fun getFunctionsByLibrary(library: String): List<KlangFun> =
        _functions.values.filter { it.library == library }.sortedBy { it.name }

    fun search(query: String): List<KlangFun> {
        val lowerQuery = query.lowercase()
        return _functions.values.filter { func ->
            func.name.lowercase().contains(lowerQuery) ||
                    func.aliases.any { it.lowercase().contains(lowerQuery) } ||
                    func.tags.any { it.lowercase().contains(lowerQuery) } ||
                    func.category.lowercase().contains(lowerQuery) ||
                    func.library.lowercase().contains(lowerQuery)
        }.sortedBy { it.name }
    }
}
