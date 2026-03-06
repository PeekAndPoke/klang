package io.peekandpoke.klang.script.types

data class KlangParam(
    val name: String,
    val type: KlangType,
    val isVararg: Boolean = false,
    val description: String = "",
    val uitools: List<String> = emptyList(),
) {
    fun render(): String = buildString {
        if (isVararg) append("vararg ")
        append("$name: ${type.render()}")
    }
}
