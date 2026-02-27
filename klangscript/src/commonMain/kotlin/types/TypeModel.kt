package io.peekandpoke.klang.script.types

data class TypeModel(
    val simpleName: String,
    val isTypeAlias: Boolean = false,
    val isNullable: Boolean = false,
    val unionMembers: List<TypeModel>? = null,
) {
    val isUnion: Boolean get() = !unionMembers.isNullOrEmpty()

    fun render(): String = buildString {
        append(simpleName)
        if (isNullable) append("?")
    }

    override fun toString(): String = render()
}
