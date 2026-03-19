package io.peekandpoke.klang.script.types

/**
 * Describes a parameter of a [KlangCallable].
 *
 * @param name Parameter name
 * @param type Parameter type
 * @param isVararg Whether this parameter accepts a variable number of arguments
 * @param description Human-readable parameter description
 * @param uitools UI tool identifiers associated with this parameter (e.g., for editor widgets)
 */
data class KlangParam(
    val name: String,
    val type: KlangType,
    val isVararg: Boolean = false,
    val description: String = "",
    val uitools: List<String> = emptyList(),
    val subFields: Map<String, String> = emptyMap(),
) {
    /**
     * Render this parameter as a signature fragment.
     *
     * @return Formatted string like `vararg name: Type`
     */
    fun render(): String = buildString {
        if (isVararg) append("vararg ")
        append("$name: ${type.render()}")
    }
}
