package io.peekandpoke.klang.script.types

data class KlangFun(
    val name: String,
    val variants: List<KlangDecl>,
    val category: String,
    val tags: List<String> = emptyList(),
    val library: String = "",
    val aliases: List<String> = emptyList(),
)
