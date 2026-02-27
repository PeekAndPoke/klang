package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.KBCallBlock
import io.peekandpoke.klang.blocks.model.KBChainStmt

data class DndCtrl(
    val state: DndState?,
    val startPaletteDrag: (funcName: String, x: Double, y: Double) -> Unit,
    val startCanvasDrag: (stmtId: String, chain: KBChainStmt, x: Double, y: Double) -> Unit,
    val startNestedBlockDrag: (block: KBCallBlock, x: Double, y: Double) -> Unit,
)

class DndState(
    val ghostX: Double,
    val ghostY: Double,
    val ghostLabel: String,
    /** Drop onto a canvas row gap at the given insertion index. */
    val onDropToPosition: ((index: Int) -> Unit)?,
    /** Drop onto the end of an existing chain (append). */
    val onDropToChain: ((chainId: String) -> Unit)?,
    /** Drop into a specific block slot (nest as KBNestedChainArg). */
    val onDropToSlot: ((stmtId: String, blockId: String, slotIdx: Int) -> Unit)?,
)
