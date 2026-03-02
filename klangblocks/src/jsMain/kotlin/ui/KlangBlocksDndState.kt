package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.DropDestination
import io.peekandpoke.klang.blocks.model.DropTargetType
import io.peekandpoke.klang.blocks.model.KBCallBlock
import io.peekandpoke.klang.blocks.model.KBChainStmt

data class DndCtrl(
    val state: DndState?,
    val startPaletteDrag: PaletteDragStarter,
    val startCanvasDrag: CanvasDragStarter,
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

    /** SAM interface for starting a block drag (top-level or nested), allowing named arguments at the call site. */
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

/** Exposed to drop zone components so they can advertise which destination types they accept. */
typealias DropTarget = DropTargetType

class DndState(
    val ghostX: Double,
    val ghostY: Double,
    val ghostLabel: String,
    /** Estimated pixel width of the ghost element — used to size hovered drop zones. */
    val ghostWidth: Double,
    /** Set of destination types this drag operation accepts. */
    val targets: Set<DropTarget>,
    /** Single handler for all drop destinations. */
    val onDrop: (DropDestination) -> Unit,
    /**
     * ID of the chain that is the source of the current drag (null for palette drags).
     * Drop zones belonging to this chain suppress themselves during the drag.
     */
    val sourceChainId: String? = null,
) {
    fun accepts(target: DropTarget): Boolean = target in targets
}
