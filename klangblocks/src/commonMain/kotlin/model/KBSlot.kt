package io.peekandpoke.klang.blocks.model

data class KBSlot(
    val index: Int,
    val name: String,
    val kind: KBSlotKind,
    val isVararg: Boolean = false,
)
