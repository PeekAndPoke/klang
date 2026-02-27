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

enum class KlangMutability { READ_ONLY, READ_WRITE, WRITE_ONLY }

data class KlangProperty(
    val name: String,
    val owner: KlangType? = null,
    val type: KlangType,
    val mutability: KlangMutability = KlangMutability.READ_ONLY,
    override val description: String = "",
    override val returnDoc: String = "",
    override val samples: List<String> = emptyList(),
) : KlangDecl {
    override val signature: String
        get() = buildString {
            when (mutability) {
                KlangMutability.READ_ONLY -> append("val ")
                KlangMutability.READ_WRITE -> append("var ")
                KlangMutability.WRITE_ONLY -> { /* no prefix — mutability shown as UI badge only */
                }
            }
            owner?.let { append("${it.render()}.") }
            append(name)
            append(": ${type.render()}")
        }
}
