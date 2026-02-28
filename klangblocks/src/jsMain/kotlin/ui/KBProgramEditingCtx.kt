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

    /** Drop a palette block directly into a slot (creates a single-block nested chain, preserving any existing string). */
    fun commitPaletteDropToSlot(funcName: String, targetStmtId: String, blockId: String, slotIndex: Int) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = true)
        update { current ->
            current.copy(
                statements = updateSlotDropInStmts(current.statements, blockId, slotIndex, listOf(newBlock))
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

    /** Append a palette block to an existing chain (searches top-level and nested chains). */
    fun commitChainAppend(chainId: String, funcName: String) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
        update { current ->
            current.copy(statements = appendBlockToChain(current.statements, chainId, newBlock))
        }
    }

    /** Append a canvas chain's blocks onto another chain, removing the source row.
     *  The target chain may be top-level or nested inside a slot. */
    fun commitCanvasChainAppend(sourceStmtId: String, sourceChain: KBChainStmt, targetChainId: String) {
        if (sourceStmtId == targetChainId) return
        val sourceBlocks = sourceChain.steps.filterIsInstance<KBCallBlock>().map { it.copy(isHead = false) }
        update { current ->
            val stripped = current.copy(statements = current.statements.filter { it.id != sourceStmtId })
            stripped.copy(
                statements = sourceBlocks.fold(stripped.statements) { stmts, block ->
                    appendBlockToChain(stmts, targetChainId, block)
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

    /**
     * Update the string literal head of the chain identified by [chainId].
     * If [newValue] is empty, the literal item is removed and the first call block becomes the head.
     */
    fun onStringLiteralItemChanged(chainId: String, newValue: String) {
        update { current ->
            current.copy(statements = updateStringLiteralItemInStmts(current.statements, chainId, newValue))
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

    // ---- Multi-block move (single block or tail chain from canvas) -----------------

    /** Remove [blocks] from wherever they live and place them as a new top-level chain at [index]. */
    fun commitMoveBlocksToPosition(blocks: List<KBCallBlock>, index: Int) {
        val newChain = KBChainStmt(
            id = uuid(),
            steps = blocks.mapIndexed { i, b -> b.copy(isHead = i == 0) },
        )
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            val mutable = stmts.toMutableList()
            mutable.add(index.coerceIn(0, mutable.size), newChain)
            current.copy(statements = mutable)
        }
    }

    /** Remove [blocks] from wherever they live and append them to chain [targetChainId]. */
    fun commitMoveBlocksToChain(blocks: List<KBCallBlock>, targetChainId: String) {
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            current.copy(
                statements = blocks.fold(stmts) { s, block ->
                    appendBlockToChain(s, targetChainId, block.copy(isHead = false))
                }
            )
        }
    }

    /** Remove [blocks] from wherever they live and insert them before [insertBeforeBlockId] in [targetChainId]. */
    fun commitMoveBlocksToChainAt(blocks: List<KBCallBlock>, targetChainId: String, insertBeforeBlockId: String?) {
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            // Insert in forward order; each block is inserted before the same target so they land in order.
            current.copy(
                statements = blocks.fold(stmts) { s, block ->
                    insertBlockIntoChain(s, targetChainId, block.copy(isHead = false), insertBeforeBlockId)
                }
            )
        }
    }

    /** Remove [blocks] from wherever they live and place them as a nested chain in a slot, preserving any existing string. */
    fun commitMoveBlocksToSlot(blocks: List<KBCallBlock>, targetStmtId: String, blockId: String, slotIndex: Int) {
        update { current ->
            val existingArg = findArgInStmts(current.statements, blockId, slotIndex)
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            val movedBlocks = blocks.mapIndexed { i, b -> b.copy(isHead = i == 0) }
            val newChain = buildSlotDropChain(existingArg, movedBlocks)
            current.copy(
                statements = updateBlockInStmts(stmts, blockId, slotIndex, KBNestedChainArg(newChain))
            )
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
                    val newSteps = removeBlockFromItems(stmt.steps, blockId).fixHeads()
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
                    val newSteps = removeBlockFromItems(argValue.chain.steps, blockId).fixHeads()
                    if (newSteps.filterIsInstance<KBCallBlock>().isEmpty()) {
                        // No call blocks remain — restore as plain string if there was a literal head
                        val literalHead = newSteps.filterIsInstance<KBStringLiteralItem>().firstOrNull()
                        if (literalHead != null) KBStringArg(literalHead.value) else KBEmptyArg("")
                    } else argValue.copy(chain = argValue.chain.copy(steps = newSteps))
                }

                else -> argValue
            }
        }

    /** Insert [block] into the chain identified by [chainId], before the block with [insertBeforeBlockId].
     *  Searches top-level and nested chains.  If [insertBeforeBlockId] is null the block is appended. */
    private fun insertBlockIntoChain(
        stmts: List<KBStmt>,
        chainId: String,
        block: KBCallBlock,
        insertBeforeBlockId: String?,
    ): List<KBStmt> = stmts.map { stmt ->
        when {
            stmt is KBChainStmt && stmt.id == chainId -> {
                val steps = stmt.steps.toMutableList()
                steps.add(insertIndexFor(steps, insertBeforeBlockId), block)
                stmt.copy(steps = steps.fixHeads())
            }

            stmt is KBChainStmt ->
                stmt.copy(steps = insertBlockIntoChainInItems(stmt.steps, chainId, block, insertBeforeBlockId))

            else -> stmt
        }
    }

    private fun insertBlockIntoChainInItems(
        items: List<KBChainItem>, chainId: String, block: KBCallBlock, insertBeforeBlockId: String?,
    ): List<KBChainItem> = items.map { item ->
        when (item) {
            is KBCallBlock -> item.copy(args = insertBlockIntoChainInArgs(item.args, chainId, block, insertBeforeBlockId))
            else -> item
        }
    }

    private fun insertBlockIntoChainInArgs(
        args: List<KBArgValue>, chainId: String, block: KBCallBlock, insertBeforeBlockId: String?,
    ): List<KBArgValue> = args.map { argValue ->
        when {
            argValue is KBNestedChainArg && argValue.chain.id == chainId -> {
                val steps = argValue.chain.steps.toMutableList()
                steps.add(insertIndexFor(steps, insertBeforeBlockId), block)
                argValue.copy(chain = argValue.chain.copy(steps = steps.fixHeads()))
            }

            argValue is KBNestedChainArg ->
                argValue.copy(
                    chain = argValue.chain.copy(
                        steps = insertBlockIntoChainInItems(argValue.chain.steps, chainId, block, insertBeforeBlockId)
                    )
                )

            else -> argValue
        }
    }

    private fun insertIndexFor(steps: List<KBChainItem>, insertBeforeBlockId: String?): Int =
        if (insertBeforeBlockId == null) steps.size
        else steps.indexOfFirst { it is KBCallBlock && it.id == insertBeforeBlockId }.takeIf { it >= 0 } ?: steps.size

    /** Append [block] to the chain identified by [chainId], searching top-level and nested chains. */
    private fun appendBlockToChain(stmts: List<KBStmt>, chainId: String, block: KBCallBlock): List<KBStmt> =
        stmts.map { stmt ->
            when {
                stmt is KBChainStmt && stmt.id == chainId ->
                    stmt.copy(steps = (stmt.steps + block).fixHeads())

                stmt is KBChainStmt ->
                    stmt.copy(steps = appendBlockToChainInItems(stmt.steps, chainId, block))

                else -> stmt
            }
        }

    private fun appendBlockToChainInItems(
        items: List<KBChainItem>, chainId: String, block: KBCallBlock,
    ): List<KBChainItem> = items.map { item ->
        when (item) {
            is KBCallBlock -> item.copy(args = appendBlockToChainInArgs(item.args, chainId, block))
            else -> item
        }
    }

    private fun appendBlockToChainInArgs(
        args: List<KBArgValue>, chainId: String, block: KBCallBlock,
    ): List<KBArgValue> = args.map { argValue ->
        when {
            argValue is KBNestedChainArg && argValue.chain.id == chainId ->
                argValue.copy(chain = argValue.chain.copy(steps = (argValue.chain.steps + block).fixHeads()))

            argValue is KBNestedChainArg ->
                argValue.copy(
                    chain = argValue.chain.copy(
                        steps = appendBlockToChainInItems(argValue.chain.steps, chainId, block)
                    )
                )

            else -> argValue
        }
    }

    // ---- Slot-drop helpers -------------------------------------------------

    /**
     * Builds a [KBChainStmt] for dropping [newBlocks] into a slot.
     * If [existingArg] is a non-empty [KBStringArg], prepends a [KBStringLiteralItem] so the
     * string is preserved as the chain receiver (e.g. `"C4".transpose(1)`).
     */
    private fun buildSlotDropChain(existingArg: KBArgValue?, newBlocks: List<KBCallBlock>): KBChainStmt {
        val literalHead = (existingArg as? KBStringArg)?.takeIf { it.value.isNotEmpty() }
            ?.let { KBStringLiteralItem(it.value) }
        val steps: List<KBChainItem> = listOfNotNull(literalHead) +
                newBlocks.mapIndexed { i, b -> b.copy(isHead = literalHead == null && i == 0) }
        return KBChainStmt(id = uuid(), steps = steps)
    }

    /** Recursively find and update a slot drop target by [blockId], preserving any existing string. */
    private fun updateSlotDropInStmts(
        stmts: List<KBStmt>, blockId: String, slotIndex: Int, newBlocks: List<KBCallBlock>,
    ): List<KBStmt> = stmts.map { stmt ->
        when (stmt) {
            is KBChainStmt -> stmt.copy(steps = updateSlotDropInItems(stmt.steps, blockId, slotIndex, newBlocks))
            else -> stmt
        }
    }

    private fun updateSlotDropInItems(
        items: List<KBChainItem>, blockId: String, slotIndex: Int, newBlocks: List<KBCallBlock>,
    ): List<KBChainItem> = items.map { item ->
        when {
            item is KBCallBlock && item.id == blockId -> {
                val newChain = buildSlotDropChain(item.args.getOrNull(slotIndex), newBlocks)
                val newArgs = item.args.toMutableList()
                while (newArgs.size <= slotIndex) newArgs.add(KBEmptyArg(""))
                newArgs[slotIndex] = KBNestedChainArg(newChain)
                item.copy(args = newArgs.toList())
            }

            item is KBCallBlock -> item.copy(args = updateSlotDropInArgs(item.args, blockId, slotIndex, newBlocks))
            else -> item
        }
    }

    private fun updateSlotDropInArgs(
        args: List<KBArgValue>, blockId: String, slotIndex: Int, newBlocks: List<KBCallBlock>,
    ): List<KBArgValue> = args.map { arg ->
        when (arg) {
            is KBNestedChainArg -> arg.copy(
                chain = arg.chain.copy(steps = updateSlotDropInItems(arg.chain.steps, blockId, slotIndex, newBlocks))
            )

            else -> arg
        }
    }

    /** Lookup current arg at (blockId, slotIndex) in the tree, used before a slot-replace. */
    private fun findArgInStmts(stmts: List<KBStmt>, blockId: String, slotIndex: Int): KBArgValue? {
        for (stmt in stmts) {
            if (stmt is KBChainStmt) findArgInItems(stmt.steps, blockId, slotIndex)?.let { return it }
        }
        return null
    }

    private fun findArgInItems(items: List<KBChainItem>, blockId: String, slotIndex: Int): KBArgValue? {
        for (item in items) {
            if (item is KBCallBlock) {
                if (item.id == blockId) return item.args.getOrNull(slotIndex)
                findArgInArgs(item.args, blockId, slotIndex)?.let { return it }
            }
        }
        return null
    }

    private fun findArgInArgs(args: List<KBArgValue>, blockId: String, slotIndex: Int): KBArgValue? {
        for (arg in args) {
            if (arg is KBNestedChainArg) findArgInItems(arg.chain.steps, blockId, slotIndex)?.let { return it }
        }
        return null
    }

    // ---- String literal item helpers ----------------------------------------

    private fun updateStringLiteralItemInStmts(stmts: List<KBStmt>, chainId: String, newValue: String): List<KBStmt> =
        stmts.map { stmt ->
            when (stmt) {
                is KBChainStmt -> if (stmt.id == chainId)
                    stmt.copy(steps = applyStringLiteralChange(stmt.steps, newValue))
                else
                    stmt.copy(steps = updateStringLiteralItemInItems(stmt.steps, chainId, newValue))

                else -> stmt
            }
        }

    private fun updateStringLiteralItemInItems(items: List<KBChainItem>, chainId: String, newValue: String): List<KBChainItem> =
        items.map { item ->
            when (item) {
                is KBCallBlock -> item.copy(args = updateStringLiteralItemInArgs(item.args, chainId, newValue))
                else -> item
            }
        }

    private fun updateStringLiteralItemInArgs(args: List<KBArgValue>, chainId: String, newValue: String): List<KBArgValue> =
        args.map { arg ->
            when {
                arg is KBNestedChainArg && arg.chain.id == chainId ->
                    arg.copy(chain = arg.chain.copy(steps = applyStringLiteralChange(arg.chain.steps, newValue)))

                arg is KBNestedChainArg ->
                    arg.copy(chain = arg.chain.copy(steps = updateStringLiteralItemInItems(arg.chain.steps, chainId, newValue)))

                else -> arg
            }
        }

    /** Apply a string literal value change: update or remove the leading [KBStringLiteralItem]. */
    private fun applyStringLiteralChange(steps: List<KBChainItem>, newValue: String): List<KBChainItem> {
        if (steps.firstOrNull() !is KBStringLiteralItem) return steps
        return if (newValue.isEmpty()) steps.drop(1).fixHeads()
        else listOf(KBStringLiteralItem(newValue)) + steps.drop(1)
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
