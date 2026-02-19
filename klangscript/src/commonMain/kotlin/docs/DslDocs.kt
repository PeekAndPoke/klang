package io.peekandpoke.klang.script.docs

/**
 * Type of DSL function variant based on how it's invoked.
 */
enum class DslType {
    /** Top-level function: `seq("a", "b")` */
    TOP_LEVEL,

    /** Pattern extension method: `pattern.seq("a", "b")` */
    EXTENSION_METHOD,

    /** Property accessor: `pattern.prop` */
    PROPERTY
}

/**
 * Documentation for a single parameter.
 */
data class ParamDoc(
    /** Parameter name */
    val name: String,

    /** Parameter type signature */
    val type: String,

    /** Description of what the parameter does */
    val description: String,
)

/**
 * Documentation for one overload variant of a function.
 */
data class VariantDoc(
    /** Type of variant (top-level, extension, property) */
    val type: DslType,

    /** Full signature (Kotlin or JavaScript style) */
    val signature: String,

    /** Description of what this variant does */
    val description: String,

    /** Parameter documentation */
    val params: List<ParamDoc> = emptyList(),

    /** Description of return value */
    val returnDoc: String = "",

    /** Example code snippets */
    val samples: List<String> = emptyList(),
)

/**
 * Documentation for a complete DSL function (all variants).
 */
data class FunctionDoc(
    /** Function name */
    val name: String,

    /** All overload variants of this function */
    val variants: List<VariantDoc>,

    /** Primary category (structural, synthesis, effects, etc.) */
    val category: String,

    /** Additional searchable tags */
    val tags: List<String> = emptyList(),

    /** Library that provides this function */
    val library: String = "",

    /** Alternative names for this function (aliases) */
    val aliases: List<String> = emptyList(),
)

/**
 * Central registry of DSL function documentation for all libraries.
 *
 * This serves as the single source of truth for documentation,
 * consumed by IDE completion, CodeMirror autocomplete, frontend UI, and static docs.
 */
object DslDocsRegistry {

    private val _functions = mutableMapOf<String, FunctionDoc>()

    /**
     * All registered function documentation.
     */
    val functions: Map<String, FunctionDoc>
        get() = _functions.toMap()

    /**
     * Register documentation for a function.
     *
     * If a function with the same name already exists, it will be replaced.
     *
     * @param doc The function documentation to register
     */
    fun register(doc: FunctionDoc) {
        _functions[doc.name] = doc
    }

    /**
     * Register multiple function docs at once.
     */
    fun registerAll(docs: List<FunctionDoc>) {
        docs.forEach { register(it) }
    }

    /**
     * Register multiple function docs at once (map variant).
     */
    fun registerAll(docs: Map<String, FunctionDoc>) {
        docs.values.forEach { register(it) }
    }

    /**
     * Clear all registered documentation (useful for testing).
     */
    fun clear() {
        _functions.clear()
    }

    /**
     * Get documentation for a specific function.
     */
    fun get(functionName: String): FunctionDoc? {
        return _functions[functionName]
    }

    /**
     * Get all function names, sorted alphabetically.
     */
    val functionNames: List<String>
        get() = _functions.keys.sorted()

    /**
     * Get all categories, sorted alphabetically.
     */
    val categories: List<String>
        get() = _functions.values.map { it.category }.distinct().sorted()

    /**
     * Get all libraries, sorted alphabetically.
     */
    val libraries: List<String>
        get() = _functions.values.map { it.library }.filter { it.isNotEmpty() }.distinct().sorted()

    /**
     * Get all functions in a specific category.
     */
    fun getFunctionsByCategory(category: String): List<FunctionDoc> {
        return _functions.values.filter { it.category == category }.sortedBy { it.name }
    }

    /**
     * Get all functions from a specific library.
     */
    fun getFunctionsByLibrary(library: String): List<FunctionDoc> {
        return _functions.values.filter { it.library == library }.sortedBy { it.name }
    }

    /**
     * Search functions by name, alias, tag, or category.
     */
    fun search(query: String): List<FunctionDoc> {
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
