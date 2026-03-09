package io.peekandpoke.klang.script.types

data class KlangType(
    val simpleName: String,
    val isTypeAlias: Boolean = false,
    val isNullable: Boolean = false,
    val unionMembers: List<KlangType>? = null,
) {
    val isUnion: Boolean get() = !unionMembers.isNullOrEmpty()

    fun render(): String = buildString {
        append(simpleName)
        if (isNullable) append("?")
    }

    override fun toString(): String = render()
}
