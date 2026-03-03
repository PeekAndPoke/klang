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

    /** Replace the block identified by [targetBlockId] in-place with the dropped payload. */
    data class ReplaceBlock(val targetBlockId: String) : DropDestination

    /**
     * Insert into a chain immediately after [afterBlockId], i.e. before any following [KBNewlineHint].
     * Used by segment-end drop zones so dropped blocks stay on the same visual row.
     */
    data class ChainInsertAfterBlock(val chainId: String, val afterBlockId: String) : DropDestination
}

/** Enumeration of destination types; used by [DropTarget][io.peekandpoke.klang.blocks.ui.DropTarget]. */
enum class DropTargetType { RowGap, ChainEnd, ChainInsert, EmptySlot, ReplaceBlock }
