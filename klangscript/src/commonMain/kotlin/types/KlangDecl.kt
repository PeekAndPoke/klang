package io.peekandpoke.klang.script.types

sealed interface KlangDecl {
    val description: String
    val returnDoc: String
    val samples: List<String>
    val signature: String
}

data class KlangCallable(
    val name: String,
    val receiver: KlangType? = null,
    val params: List<KlangParam>,
    val returnType: KlangType? = null,
    override val description: String = "",
    override val returnDoc: String = "",
    override val samples: List<String> = emptyList(),
) : KlangDecl {
    override val signature: String
        get() = buildString {
            receiver?.let { append("${it.render()}.") }
            append(name)
            append("(")
            append(params.joinToString(", ") { it.render() })
            append(")")
            returnType?.let { append(": ${it.render()}") }
        }
}

data class KlangObject(
    val name: String,
    val owner: KlangType? = null,
    val type: KlangType,
    override val description: String = "",
    override val returnDoc: String = "",
    override val samples: List<String> = emptyList(),
) : KlangDecl {
    override val signature: String
        get() = buildString {
            owner?.let { append("${it.render()}.") }
            append(name)
            append(": ${type.render()}")
        }
}
