package io.peekandpoke.klang.script.types

/** Base interface for a KlangScript declaration (callable or property). */
sealed interface KlangDecl {
    /** Human-readable description of this declaration. */
    val description: String

    /** Documentation for the return value. */
    val returnDoc: String

    /** Example code snippets. */
    val samples: List<String>

    /** Rendered signature string (e.g., `note(pattern: String): Pattern`). */
    val signature: String
}

/**
 * A callable declaration (function or method).
 *
 * @param name Function/method name
 * @param receiver Optional receiver type for extension methods
 * @param params Ordered list of parameter descriptors
 * @param returnType Optional return type
 * @param description Human-readable description
 * @param returnDoc Documentation for the return value
 * @param samples Example code snippets
 */
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

/** Mutability mode for a [KlangProperty]. */
enum class KlangMutability { READ_ONLY, READ_WRITE, WRITE_ONLY }

/**
 * A property declaration.
 *
 * @param name Property name
 * @param owner Optional owning type
 * @param type The property's type
 * @param mutability Read/write access mode
 * @param description Human-readable description
 * @param returnDoc Documentation for the value
 * @param samples Example code snippets
 */
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
