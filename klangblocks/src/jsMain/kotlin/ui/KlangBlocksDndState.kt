package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.KBCallBlock
import io.peekandpoke.klang.blocks.model.KBChainStmt

data class DndCtrl(
    val state: DndState?,
    val startPaletteDrag: PaletteDragStarter,
    val startCanvasDrag: CanvasDragStarter,
    val startNestedBlockDrag: NestedBlockDragStarter,
    val startBlockDrag: BlockDragStarter,
) {
    /** SAM interface for starting a palette drag. */
    fun interface PaletteDragStarter {
        operator fun invoke(funcName: String, x: Double, y: Double)
    }

    /** SAM interface for starting a canvas chain drag. */
    fun interface CanvasDragStarter {
        operator fun invoke(stmtId: String, chain: KBChainStmt, x: Double, y: Double)
    }

    /** SAM interface for starting a nested block drag. */
    fun interface NestedBlockDragStarter {
        operator fun invoke(block: KBCallBlock, x: Double, y: Double)
    }

    /** SAM interface for starting a block drag, allowing named arguments at the call site. */
    fun interface BlockDragStarter {
        operator fun invoke(
            sourceChainId: String,
            sourceChain: KBChainStmt,
            block: KBCallBlock,
            ctrlHeld: Boolean,
            x: Double,
            y: Double,
        )
    }
}

class DndState(
    val ghostX: Double,
    val ghostY: Double,
    val ghostLabel: String,
    val onDropToPosition: DropToPositionHandler?,
    val onDropToChain: DropToChainHandler?,
    val onDropToSlot: DropToSlotHandler?,
    val onDropToChainAt: DropToChainAtHandler?,
) {
    /** SAM interface for dropping onto a canvas row gap at the given insertion index. */
    fun interface DropToPositionHandler {
        operator fun invoke(index: Int)
    }

    /** SAM interface for dropping onto the end of an existing chain (append). */
    fun interface DropToChainHandler {
        operator fun invoke(chainId: String)
    }

    /** SAM interface for dropping into a specific block slot (nest as KBNestedChainArg). */
    fun interface DropToSlotHandler {
        operator fun invoke(stmtId: String, blockId: String, slotIdx: Int)
    }

    /**
     * SAM interface for dropping into a specific position within a chain.
     * Pass null for insertBeforeBlockId to append to the end of the chain.
     */
    fun interface DropToChainAtHandler {
        operator fun invoke(chainId: String, insertBeforeBlockId: String?)
    }
}
