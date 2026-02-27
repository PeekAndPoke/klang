package io.peekandpoke.klang.blocks.model

import de.peekandpoke.mutator.Mutable

@Mutable
data class KBProgram(
    val statements: List<KBStmt> = emptyList(),
)
