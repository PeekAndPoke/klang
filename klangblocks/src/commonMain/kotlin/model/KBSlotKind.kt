package io.peekandpoke.klang.blocks.model

/**
 * The type descriptor for a [KBSlot], controlling which [KBArgValue] subtypes
 * are valid and whether the slot lights up as a drop target during a drag.
 */
sealed interface KBSlotKind {
    /** True when a dropped block/chain is a valid value for this slot. */
    val acceptsBlock: Boolean get() = false

    /** Accepts a plain string literal ([KBStringArg]) or a dropped block/chain ([KBNestedChainArg]). */
    data object Str : KBSlotKind {
        override val acceptsBlock: Boolean = true
    }

    /** Accepts a numeric literal ([KBNumberArg]). */
    data object Num : KBSlotKind

    /** Accepts a boolean literal ([KBBoolArg]). */
    data object Bool : KBSlotKind

    /**
     * Accepts a function-call chain ([KBNestedChainArg]) or an identifier ([KBIdentifierArg]).
     * Used for parameters whose type is `StrudelPattern` / `Pattern`.
     */
    data object PatternResult : KBSlotKind {
        override val acceptsBlock: Boolean = true
    }

    /**
     * A union of multiple slot kinds, e.g. `PatternLike = Str | Num | PatternResult`.
     * The slot accepts any value that is compatible with at least one member kind.
     * [acceptsBlock] is true when any member accepts blocks.
     */
    data class Union(val members: List<KBSlotKind>) : KBSlotKind {
        override val acceptsBlock: Boolean get() = members.any { it.acceptsBlock }
    }

    /**
     * A named object or domain type that has no dedicated primitive kind,
     * e.g. a custom class from a library. Rendered like a plain slot;
     * the type name is available for future tooling.
     */
    data class NamedObject(val typeName: String) : KBSlotKind
}

/** True when this kind can hold a plain string value (directly or as a union member). */
val KBSlotKind.isStringish: Boolean
    get() = when (this) {
        KBSlotKind.Str -> true
        is KBSlotKind.Union -> members.any { it is KBSlotKind.Str }
        else -> false
    }
