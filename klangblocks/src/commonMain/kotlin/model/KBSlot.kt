package io.peekandpoke.klang.blocks.model

/**
 * Describes one parameter slot of a [KBCallBlock] as it should appear in the editor.
 *
 * Slots are derived at render time from the function's documentation entry
 * ([KBTypeMapping.slotsFor]) and are never stored in the model — they are
 * purely a view-layer concept used to pair parameter metadata with the
 * block's current [KBArgValue] list.
 *
 * @property index The position in the block's [KBCallBlock.args] list that this slot reads/writes.
 * @property name  The parameter name shown as a placeholder when the slot is empty.
 * @property kind  The type descriptor that controls what values and drop targets are accepted.
 */
sealed interface KBSlot {
    val index: Int
    val name: String
    val kind: KBSlotKind
}

/**
 * A slot that holds exactly one value — the common case for regular parameters.
 *
 * Maps directly to a single position in the args list at [index].
 */
data class KBSingleSlot(
    override val index: Int,
    override val name: String,
    override val kind: KBSlotKind,
) : KBSlot

/**
 * A slot that represents a variadic (`vararg`) parameter — the function accepts
 * zero or more values of this kind.
 *
 * In the editor, a vararg slot expands into one rendered item per filled argument
 * plus one trailing empty item at the next available index, allowing the user to
 * keep adding values. Each rendered item writes to its own position in the args list.
 */
data class KBVarArgSlot(
    override val index: Int,
    override val name: String,
    override val kind: KBSlotKind,
) : KBSlot
