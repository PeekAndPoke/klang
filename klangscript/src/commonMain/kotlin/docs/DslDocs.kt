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
    PROPERTY,

    /** Named object/constant pattern: `sine`, `berlin`, `silence` */
    OBJECT
}

/**
 * Represents a Kotlin type in structured form.
 *
 * Used to build DSL function signatures from structured data rather than raw strings.
 */
data class TypeModel(
    /** Simple (unqualified) type name, e.g. "StrudelPattern", "PatternLike" */
    val simpleName: String,

    /** True when this type is a type alias (e.g. PatternLike = Any) */
    val isTypeAlias: Boolean = false,

    /** True when the type is nullable (e.g. String?) */
    val isNullable: Boolean = false,
) {
    /** Renders the type as a human-readable string. */
    fun render(): String = buildString {
        append(simpleName)
        if (isNullable) append("?")
    }

    override fun toString(): String = render()
}

/**
 * Represents a single parameter in a DSL function signature.
 */
data class ParamModel(
    /** Parameter name */
    val name: String,

    /** Parameter type */
    val type: TypeModel,

    /** True when the parameter is a vararg */
    val isVararg: Boolean = false,

    /** Description of what the parameter does */
    val description: String = "",
) {
    /** Renders the parameter as it appears in a signature string. */
    fun render(): String = buildString {
        if (isVararg) append("vararg ")
        append("$name: ${type.render()}")
    }
}

/**
 * Structured representation of a DSL function or property signature.
 *
 * Three modes based on the `params` field:
 * - `params = null`        → property/object, renders without parentheses: `sine: StrudelPattern`
 * - `params = emptyList()` → callable with no parameter info: `accelerate(): StrudelPattern`
 * - `params = [...]`       → fully-parameterized: `seq(vararg patterns: PatternLike): StrudelPattern`
 */
data class SignatureModel(
    /** Function or property name */
    val name: String,

    /** Receiver type for extension functions/properties, null for top-level */
    val receiver: TypeModel? = null,

    /** Parameter list. null = property (no parens); emptyList = callable with no params */
    val params: List<ParamModel>? = null,

    /** Return type */
    val returnType: TypeModel? = null,
) {
    /** Renders the signature as a human-readable string. */
    fun render(): String = buildString {
        receiver?.let { append("${it.render()}.") }
        append(name)
        if (params != null) {
            append("(")
            append(params.joinToString(", ") { it.render() })
            append(")")
        }
        returnType?.let { append(": ${it.render()}") }
    }
}

/**
 * Documentation for one overload variant of a function.
 */
data class VariantDoc(
    /** Type of variant (top-level, extension, property) */
    val type: DslType,

    /** Structured signature model — primary source of truth */
    val signatureModel: SignatureModel,

    /** Description of what this variant does */
    val description: String,

    /** Description of return value */
    val returnDoc: String = "",

    /** Example code snippets */
    val samples: List<String> = emptyList(),
) {
    /** Full signature rendered from the structured model */
    val signature: String get() = signatureModel.render()

    /** Parameter list derived from the signature model */
    val params: List<ParamModel> get() = signatureModel.params ?: emptyList()
}

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
 * Registry of DSL function documentation for all libraries.
 *
 * This serves as a source of truth for documentation,
 * consumed by IDE completion, CodeMirror autocomplete, frontend UI, and static docs.
 *
 * Can be instantiated for testing or used via the global [DslDocsRegistry.global] instance.
 */
class DslDocsRegistry {

    companion object {
        /**
         * Global default registry instance.
         * Use this for production code. Tests can create their own isolated instances.
         */
        val global = DslDocsRegistry()
    }

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
