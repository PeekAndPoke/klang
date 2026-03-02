package io.peekandpoke.klang.blocks.model

/** Geometry-only description of where a drop is landing. */
sealed interface DropDestination {
    /** Drop into a canvas row gap at the given insertion index. */
    data class RowGap(val index: Int) : DropDestination

    /** Append to the end of the chain identified by [chainId]. */
    data class ChainEnd(val chainId: String) : DropDestination

    /** Insert into a chain before the block identified by [insertBeforeBlockId] (null = append). */
    data class ChainInsert(val chainId: String, val insertBeforeBlockId: String?) : DropDestination

    /** Drop into the argument slot [slotIdx] of the block identified by [blockId]. */
    data class EmptySlot(val blockId: String, val slotIdx: Int) : DropDestination
}

/** Enumeration of destination types; used by [DropTarget][io.peekandpoke.klang.blocks.ui.DropTarget]. */
enum class DropTargetType { RowGap, ChainEnd, ChainInsert, EmptySlot }
