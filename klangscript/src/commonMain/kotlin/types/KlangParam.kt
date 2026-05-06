package io.peekandpoke.klang.script.types

/**
 * Describes a parameter of a [KlangCallable].
 *
 * @param name Parameter name
 * @param type Parameter type
 * @param isVararg Whether this parameter accepts a variable number of arguments
 * @param isOptional True when the parameter has a Kotlin-side default value.
 *                   Drives the `?` marker in rendered signatures and tells the
 *                   analyzer "this slot may be omitted in named calls".
 * @param defaultDoc Optional human-readable default value (e.g. `"1000"`).
 *                   Extracted from raw source by the KSP processor for display
 *                   only; never executed as code.
 * @param description Human-readable parameter description
 * @param uitools UI tool identifiers associated with this parameter (e.g., for editor widgets)
 */
data class KlangParam(
    val name: String,
    val type: KlangType,
    val isVararg: Boolean = false,
    val isOptional: Boolean = false,
    val defaultDoc: String? = null,
    val description: String = "",
    val uitools: List<String> = emptyList(),
    val subFields: Map<String, String> = emptyMap(),
) {
    /**
     * Render this parameter as a signature fragment.
     *
     * Examples:
     *   `cutoff: Number`
     *   `vararg samples: String`
     *   `q: Number? = 1.0`
     *   `q: Number?`
     *
     * @return Formatted string
     */
    fun render(): String = buildString {
        if (isVararg) append("vararg ")
        append("$name: ${type.render()}")
        // Show `?` for optional params — but skip if the type itself is already nullable
        // to avoid double `??` (e.g. `amount: PatternLike?` where PatternLike is already Any?).
        if (isOptional && !isVararg && !type.isNullable) append("?")
        if (defaultDoc != null && !isVararg) append(" = $defaultDoc")
    }
}
