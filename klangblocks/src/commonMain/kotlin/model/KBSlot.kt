package io.peekandpoke.klang.blocks.model

sealed interface KBSlot {
    val index: Int
    val name: String
    val kind: KBSlotKind
}

data class KBSingleSlot(
    override val index: Int,
    override val name: String,
    override val kind: KBSlotKind,
) : KBSlot

data class KBVarArgSlot(
    override val index: Int,
    override val name: String,
    override val kind: KBSlotKind,
) : KBSlot
