package io.peekandpoke.klang.blocks.ui

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
