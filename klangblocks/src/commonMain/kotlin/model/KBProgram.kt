package io.peekandpoke.klang.blocks.model

data class KBProgram(
    val statements: List<KBStmt> = emptyList(),
)
