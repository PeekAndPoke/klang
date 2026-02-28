package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.*

class KBProgramEditingCtx(
    initialProgram: KBProgram,
    private val onChanged: (KBProgram) -> Unit,
) {

    var program: KBProgram = initialProgram
        private set

    private val undoStack = ArrayDeque<KBProgram>()
    private val redoStack = ArrayDeque<KBProgram>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun update(block: (current: KBProgram) -> KBProgram) {
        val current = program
        val next = block(current)
        if (next != current) {
            undoStack.addLast(current)
            redoStack.clear()
            program = next
            onChanged(program)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(program)
        program = undoStack.removeLast()
        onChanged(program)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(program)
        program = redoStack.removeLast()
        onChanged(program)
    }

    // ---- Top-level program mutations -----------------------------------------------

    /** Insert a new single-block chain at a specific row index. */
    fun commitPaletteDropAtPosition(funcName: String, index: Int) {
        val chain = KBChainStmt(
            id = uuid(),
            steps = listOf(KBCallBlock(id = uuid(), funcName = funcName, isHead = true))
        )
        update { current ->
            val stmts = current.statements.toMutableList()
            stmts.add(index.coerceIn(0, stmts.size), chain)
            current.copy(statements = stmts)
        }
    }

    /** Move an existing canvas chain to a new row index. */
    fun commitMoveToPosition(stmtId: String, index: Int) {
        update { current ->
            val stmts = current.statements.toMutableList()
            val srcIndex = stmts.indexOfFirst { it.id == stmtId }
            if (srcIndex < 0) return@update current
            val chain = stmts.removeAt(srcIndex)
            val insertAt = if (index > srcIndex) (index - 1).coerceAtLeast(0) else index
            stmts.add(insertAt.coerceIn(0, stmts.size), chain)
            current.copy(statements = stmts)
        }
    }

    /** Drop a palette block directly into a slot (creates a single-block nested chain). */
    fun commitPaletteDropToSlot(funcName: String, targetStmtId: String, blockId: String, slotIndex: Int) {
        val newChain = KBChainStmt(id = uuid(), steps = listOf(KBCallBlock(id = uuid(), funcName = funcName, isHead = true)))
        update { current ->
            current.copy(
                statements = current.statements.map { stmt ->
                    if (stmt.id != targetStmtId || stmt !is KBChainStmt) stmt
                    else stmt.copy(
                        steps = stmt.steps.map { item ->
                            if (item !is KBCallBlock || item.id != blockId) item
                            else {
                                val newArgs = item.args.toMutableList()
                                while (newArgs.size <= slotIndex) newArgs.add(KBEmptyArg(""))
                                newArgs[slotIndex] = KBNestedChainArg(newChain)
                                item.copy(args = newArgs.toList())
                            }
                        }
                    )
                }
            )
        }
    }

    /** Insert a palette block at a specific position in a chain, before [insertBeforeBlockId]. */
    fun commitPaletteDropToChainAt(funcName: String, chainId: String, insertBeforeBlockId: String?) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
        update { current ->
            current.copy(statements = insertBlockIntoChain(current.statements, chainId, newBlock, insertBeforeBlockId))
        }
    }

    /** Extract a block from wherever it lives and insert it at a specific position in a chain. */
    fun commitInsertBlockInChain(draggedBlock: KBCallBlock, chainId: String, insertBeforeBlockId: String?) {
        val insertBlock = draggedBlock.copy(isHead = false)
        update { current ->
            val stripped = current.copy(statements = removeBlockFromStmts(current.statements, draggedBlock.id))
            stripped.copy(statements = insertBlockIntoChain(stripped.statements, chainId, insertBlock, insertBeforeBlockId))
        }
    }

    /** Append a palette block to an existing chain. */
    fun commitChainAppend(chainId: String, funcName: String) {
        update { current ->
            current.copy(
                statements = current.statements.map { stmt ->
                    if (stmt.id != chainId || stmt !is KBChainStmt) stmt
                    else stmt.copy(
                        steps = stmt.steps + KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
                    )
                }
            )
        }
    }

    /** Append a canvas chain's blocks onto another chain, removing the source row. */
    fun commitCanvasChainAppend(sourceStmtId: String, sourceChain: KBChainStmt, targetChainId: String) {
        if (sourceStmtId == targetChainId) return
        val sourceBlocks = sourceChain.steps.filterIsInstance<KBCallBlock>().map { it.copy(isHead = false) }
        update { current ->
            current.copy(
                statements = current.statements.mapNotNull { stmt ->
                    when {
                        stmt.id == sourceStmtId -> null
                        stmt.id == targetChainId && stmt is KBChainStmt -> stmt.copy(steps = stmt.steps + sourceBlocks)
                        else -> stmt
                    }
                }
            )
        }
    }

    /** Drop a canvas chain into a block slot; removes the source row. */
    fun commitCanvasSlotDrop(
        sourceStmtId: String,
        sourceChain: KBChainStmt,
        targetStmtId: String,
        blockId: String,
        slotIndex: Int,
    ) {
        if (sourceStmtId == targetStmtId) return
        update { current ->
            current.copy(
                statements = current.statements.mapNotNull { stmt ->
                    when {
                        stmt.id == sourceStmtId -> null
                        stmt.id == targetStmtId && stmt is KBChainStmt -> stmt.copy(
                            steps = stmt.steps.map { item ->
                                if (item !is KBCallBlock || item.id != blockId) item
                                else {
                                    val newArgs = item.args.toMutableList()
                                    while (newArgs.size <= slotIndex) newArgs.add(KBEmptyArg(""))
                                    newArgs[slotIndex] = KBNestedChainArg(sourceChain)
                                    item.copy(args = newArgs.toList())
                                }
                            }
                        )

                        else -> stmt
                    }
                }
            )
        }
    }

    fun commitImportLibrary(libraryName: String) {
        val import = KBImportStmt(id = uuid(), libraryName = libraryName)
        update { current -> current.copy(statements = listOf(import) + current.statements) }
    }

    /** Update an arg on any block at any nesting depth, located by blockId. */
    fun onArgChanged(blockId: String, slotIndex: Int, arg: KBArgValue) {
        update { current ->
            current.copy(statements = updateBlockInStmts(current.statements, blockId, slotIndex, arg))
        }
    }

    /** Remove any block at any nesting depth, located by blockId.
     *  Auto-removes the containing chain/slot if it becomes empty. */
    fun onRemoveBlock(blockId: String) {
        update { current ->
            current.copy(statements = removeBlockFromStmts(current.statements, blockId))
        }
    }

    /** Extract a nested block to a new top-level chain at the given row index. */
    fun commitNestedBlockDragToPosition(draggedBlock: KBCallBlock, index: Int) {
        val newChain = KBChainStmt(id = uuid(), steps = listOf(draggedBlock.copy(isHead = true)))
        update { current ->
            val stripped = current.copy(statements = removeBlockFromStmts(current.statements, draggedBlock.id))
            val stmts = stripped.statements.toMutableList()
            stmts.add(index.coerceIn(0, stmts.size), newChain)
            stripped.copy(statements = stmts)
        }
    }

    /** Extract a nested block and append it to an existing chain. */
    fun commitNestedBlockDragToChain(draggedBlock: KBCallBlock, targetChainId: String) {
        update { current ->
            val stripped = current.copy(statements = removeBlockFromStmts(current.statements, draggedBlock.id))
            stripped.copy(
                statements = stripped.statements.map { stmt ->
                    if (stmt.id != targetChainId || stmt !is KBChainStmt) stmt
                    else stmt.copy(steps = stmt.steps + draggedBlock.copy(isHead = false))
                }
            )
        }
    }

    /** Extract a nested block and place it into a slot of another block. */
    fun commitNestedBlockDragToSlot(draggedBlock: KBCallBlock, targetBlockId: String, targetSlotIndex: Int) {
        val newChain = KBChainStmt(id = uuid(), steps = listOf(draggedBlock.copy(isHead = true)))
        update { current ->
            val stripped = current.copy(statements = removeBlockFromStmts(current.statements, draggedBlock.id))
            stripped.copy(
                statements = updateBlockInStmts(stripped.statements, targetBlockId, targetSlotIndex, KBNestedChainArg(newChain))
            )
        }
    }

    /** Toggle the pocket layout of a specific block between HORIZONTAL and VERTICAL. */
    fun onToggleLayout(blockId: String) {
        update { current ->
            current.copy(statements = toggleLayoutInStmts(current.statements, blockId))
        }
    }

    /** Remove an entire statement (import / let / const / blank line). */
    fun onRemoveStmt(stmtId: String) {
        update { current -> current.copy(statements = current.statements.filter { it.id != stmtId }) }
    }

    /** Insert a blank line at the given row index. */
    fun insertBlankLine(index: Int) {
        val blank = KBBlankLine(id = uuid())
        update { current ->
            val stmts = current.statements.toMutableList()
            stmts.add(index.coerceIn(0, stmts.size), blank)
            current.copy(statements = stmts)
        }
    }

    // ---- Recursive tree helpers ----------------------------------------------------

    private fun updateBlockInStmts(
        stmts: List<KBStmt>,
        blockId: String,
        slotIndex: Int,
        arg: KBArgValue,
    ): List<KBStmt> = stmts.map { stmt ->
        when (stmt) {
            is KBChainStmt -> stmt.copy(steps = updateBlockInItems(stmt.steps, blockId, slotIndex, arg))
            else -> stmt
        }
    }

    private fun updateBlockInItems(
        items: List<KBChainItem>,
        blockId: String,
        slotIndex: Int,
        arg: KBArgValue,
    ): List<KBChainItem> = items.map { item ->
        when {
            item is KBCallBlock && item.id == blockId -> {
                val newArgs = item.args.toMutableList()
                while (newArgs.size <= slotIndex) newArgs.add(KBEmptyArg(""))
                newArgs[slotIndex] = arg
                item.copy(args = newArgs.toList())
            }

            item is KBCallBlock -> item.copy(args = updateBlockInArgs(item.args, blockId, slotIndex, arg))
            else -> item
        }
    }

    private fun updateBlockInArgs(
        args: List<KBArgValue>,
        blockId: String,
        slotIndex: Int,
        arg: KBArgValue,
    ): List<KBArgValue> = args.map { argValue ->
        when (argValue) {
            is KBNestedChainArg -> argValue.copy(
                chain = argValue.chain.copy(
                    steps = updateBlockInItems(argValue.chain.steps, blockId, slotIndex, arg)
                )
            )

            else -> argValue
        }
    }

    private fun removeBlockFromStmts(stmts: List<KBStmt>, blockId: String): List<KBStmt> =
        stmts.mapNotNull { stmt ->
            when (stmt) {
                is KBChainStmt -> {
                    val newSteps = removeBlockFromItems(stmt.steps, blockId)
                    if (newSteps.filterIsInstance<KBCallBlock>().isEmpty()) null
                    else stmt.copy(steps = newSteps)
                }

                else -> stmt
            }
        }

    private fun removeBlockFromItems(items: List<KBChainItem>, blockId: String): List<KBChainItem> =
        items
            .filter { !(it is KBCallBlock && it.id == blockId) }
            .map { item ->
                when (item) {
                    is KBCallBlock -> item.copy(args = removeBlockFromArgs(item.args, blockId))
                    else -> item
                }
            }

    private fun removeBlockFromArgs(args: List<KBArgValue>, blockId: String): List<KBArgValue> =
        args.map { argValue ->
            when (argValue) {
                is KBNestedChainArg -> {
                    val newSteps = removeBlockFromItems(argValue.chain.steps, blockId)
                    if (newSteps.filterIsInstance<KBCallBlock>().isEmpty()) KBEmptyArg("")
                    else argValue.copy(chain = argValue.chain.copy(steps = newSteps))
                }

                else -> argValue
            }
        }

    /** Insert [block] into the chain identified by [chainId], before the block with [insertBeforeBlockId].
     *  If [insertBeforeBlockId] is null the block is appended at the end. */
    private fun insertBlockIntoChain(
        stmts: List<KBStmt>,
        chainId: String,
        block: KBCallBlock,
        insertBeforeBlockId: String?,
    ): List<KBStmt> = stmts.map { stmt ->
        if (stmt.id != chainId || stmt !is KBChainStmt) stmt
        else {
            val steps = stmt.steps.toMutableList()
            val insertAt = if (insertBeforeBlockId == null) {
                steps.size
            } else {
                steps.indexOfFirst { it is KBCallBlock && it.id == insertBeforeBlockId }
                    .takeIf { it >= 0 } ?: steps.size
            }
            steps.add(insertAt, block)
            stmt.copy(steps = steps.fixHeads())
        }
    }

    /** Ensure the first [KBCallBlock] in a step list has isHead=true and all others isHead=false. */
    private fun List<KBChainItem>.fixHeads(): List<KBChainItem> {
        var isFirst = true
        return map { item ->
            if (item !is KBCallBlock) item
            else if (isFirst) {
                isFirst = false; item.copy(isHead = true)
            } else item.copy(isHead = false)
        }
    }

    private fun toggleLayoutInStmts(stmts: List<KBStmt>, blockId: String): List<KBStmt> =
        stmts.map { stmt ->
            when (stmt) {
                is KBChainStmt -> stmt.copy(steps = toggleLayoutInItems(stmt.steps, blockId))
                else -> stmt
            }
        }

    private fun toggleLayoutInItems(items: List<KBChainItem>, blockId: String): List<KBChainItem> =
        items.map { item ->
            when {
                item is KBCallBlock && item.id == blockId -> {
                    val newLayout = if (item.pocketLayout == KBPocketLayout.VERTICAL)
                        KBPocketLayout.HORIZONTAL else KBPocketLayout.VERTICAL
                    item.copy(pocketLayout = newLayout)
                }

                item is KBCallBlock -> item.copy(args = toggleLayoutInArgs(item.args, blockId))
                else -> item
            }
        }

    private fun toggleLayoutInArgs(args: List<KBArgValue>, blockId: String): List<KBArgValue> =
        args.map { argValue ->
            when (argValue) {
                is KBNestedChainArg -> argValue.copy(
                    chain = argValue.chain.copy(
                        steps = toggleLayoutInItems(argValue.chain.steps, blockId)
                    )
                )
                else -> argValue
            }
        }
}
