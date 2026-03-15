package io.peekandpoke.klang.script.types

/**
 * Represents a type in the KlangScript type documentation system.
 *
 * @param simpleName The unqualified type name (e.g., "String", "Pattern")
 * @param isTypeAlias Whether this type is an alias for another type
 * @param isNullable Whether this type is nullable
 * @param unionMembers Members of a union type (e.g., String | Number), or null if not a union
 */
data class KlangType(
    val simpleName: String,
    val isTypeAlias: Boolean = false,
    val isNullable: Boolean = false,
    val unionMembers: List<KlangType>? = null,
) {
    /** Whether this type is a union type. */
    val isUnion: Boolean get() = !unionMembers.isNullOrEmpty()

    /**
     * Render this type as a display string.
     *
     * @return The rendered type name with nullable suffix if applicable
     */
    fun render(): String = buildString {
        append(simpleName)
        if (isNullable) append("?")
    }

    override fun toString(): String = render()
}
