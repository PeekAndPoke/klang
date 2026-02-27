package io.peekandpoke.klang.blocks.model

sealed interface KBSlotKind {
    data object Str : KBSlotKind
    data object Num : KBSlotKind
    data object Bool : KBSlotKind
    data object PatternResult : KBSlotKind
    data class Union(val members: List<KBSlotKind>) : KBSlotKind {
        val acceptsString: Boolean get() = members.any { it is Str }
        val acceptsBlock: Boolean get() = members.any { it is PatternResult }
    }

    data class NamedObject(val typeName: String) : KBSlotKind
    data class Unknown(val typeName: String) : KBSlotKind
}
