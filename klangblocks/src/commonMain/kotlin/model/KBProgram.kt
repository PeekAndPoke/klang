package io.peekandpoke.klang.blocks.model

data class KBProgram(
    val statements: MutableList<KBStmt> = mutableListOf(),
)
