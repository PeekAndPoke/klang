package io.peekandpoke.klang.script.types

data class KlangFunSignature(
    val name: String,
    val receiver: KlangType? = null,
    /** null = property (no parens); emptyList = callable with no params */
    val params: List<KlangParam>? = null,
    val returnType: KlangType? = null,
) {
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
