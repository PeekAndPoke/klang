package io.peekandpoke.klang.blocks.model

class KBProgramEditingCtx(
    initialProgram: KBProgram,
    private val onChanged: (KBProgram) -> Unit = {},
) {
    var program: KBProgram = initialProgram
        private set

    private val undoStack = ArrayDeque<KBProgram>()
    private val redoStack = ArrayDeque<KBProgram>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun update(block: (current: KBProgram) -> KBProgram) {
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

    // ---- Drop action entry point ---------------------------------------------------

    fun execute(action: DropAction) {
        when (action) {
            is DropAction.CreateBlock -> when (val d = action.destination) {
                is DropDestination.RowGap -> createBlockAtRowGap(action.funcName, d.index)
                is DropDestination.ChainEnd -> appendBlockToChainById(d.chainId, action.funcName)
                is DropDestination.ChainInsert -> insertBlockIntoChainById(action.funcName, d.chainId, d.insertBeforeBlockId)
                is DropDestination.ChainInsertAfterBlock -> insertBlocksAfterBlockById(action.funcName, d.chainId, d.afterBlockId)
                is DropDestination.EmptySlot -> dropBlockToSlot(action.funcName, d.blockId, d.slotIdx)
                is DropDestination.ReplaceBlock -> replaceWithNewBlock(action.funcName, d.targetBlockId)
            }

            is DropAction.MoveBlocks -> when (val d = action.destination) {
                is DropDestination.RowGap -> moveBlocksToRowGap(action.blocks, d.index)
                is DropDestination.ChainEnd -> moveBlocksToChainEnd(action.blocks, d.chainId)
                is DropDestination.ChainInsert -> moveBlocksToChainInsert(action.blocks, d.chainId, d.insertBeforeBlockId)
                is DropDestination.ChainInsertAfterBlock -> moveBlocksToChainInsertAfterBlock(action.blocks, d.chainId, d.afterBlockId)
                is DropDestination.EmptySlot -> moveBlocksToSlot(action.blocks, d.blockId, d.slotIdx)
                is DropDestination.ReplaceBlock -> replaceWithMovedBlocks(action.blocks, d.targetBlockId)
            }

            is DropAction.MoveRow -> moveRow(action.sourceStmtId, action.targetIndex)

            is DropAction.Compound -> action.actions.forEach { execute(it) }
        }
    }

    // ---- Top-level program mutations (public — used directly by UI) -----------------

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

    /** Toggle a [KBNewlineHint] directly before the block identified by [blockId] in chain [chainId].
     *  Inserts a hint if none exists; removes it if one does. */
    fun onToggleNewlineBeforeBlock(chainId: String, blockId: String) {
        update { current ->
            current.copy(statements = toggleNewlineInStmts(current.statements, chainId, blockId))
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

    // ---- Drop action implementations (private) -------------------------------------

    private fun createBlockAtRowGap(funcName: String, index: Int) {
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

    private fun appendBlockToChainById(chainId: String, funcName: String) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
        update { current ->
            current.copy(statements = appendBlockToChain(current.statements, chainId, newBlock))
        }
    }

    private fun insertBlockIntoChainById(funcName: String, chainId: String, insertBeforeBlockId: String?) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
        update { current ->
            current.copy(statements = insertBlockIntoChain(current.statements, chainId, newBlock, insertBeforeBlockId))
        }
    }

    private fun dropBlockToSlot(funcName: String, blockId: String, slotIndex: Int) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = true)
        update { current ->
            current.copy(
                statements = updateSlotDropInStmts(current.statements, blockId, slotIndex, listOf(newBlock))
            )
        }
    }

    private fun moveBlocksToRowGap(blocks: List<KBCallBlock>, index: Int) {
        val clones = blocks.map { it.copy(id = uuid()) }
        val newChain = KBChainStmt(
            id = uuid(),
            steps = clones.mapIndexed { i, b -> b.copy(isHead = i == 0) },
        )
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            val mutable = stmts.toMutableList()
            mutable.add(index.coerceIn(0, mutable.size), newChain)
            current.copy(statements = mutable)
        }
    }

    private fun moveBlocksToChainEnd(blocks: List<KBCallBlock>, targetChainId: String) {
        val clones = blocks.map { it.copy(id = uuid()) }
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            current.copy(
                statements = clones.fold(stmts) { s, block ->
                    appendBlockToChain(s, targetChainId, block.copy(isHead = false))
                }
            )
        }
    }

    private fun moveBlocksToChainInsert(blocks: List<KBCallBlock>, targetChainId: String, insertBeforeBlockId: String?) {
        // No-op: inserting before a block that is itself part of the payload leaves the chain unchanged.
        val blockIds = blocks.map { it.id }.toSet()
        if (insertBeforeBlockId != null && insertBeforeBlockId in blockIds) return
        val clones = blocks.map { it.copy(id = uuid()) }
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            current.copy(
                statements = clones.fold(stmts) { s, block ->
                    insertBlockIntoChain(s, targetChainId, block.copy(isHead = false), insertBeforeBlockId)
                }
            )
        }
    }

    private fun insertBlocksAfterBlockById(funcName: String, chainId: String, afterBlockId: String) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
        update { current ->
            current.copy(statements = insertBlocksAfterBlock(current.statements, chainId, listOf(newBlock), afterBlockId))
        }
    }

    private fun moveBlocksToChainInsertAfterBlock(blocks: List<KBCallBlock>, targetChainId: String, afterBlockId: String) {
        if (afterBlockId in blocks.map { it.id }) return
        val clones = blocks.map { it.copy(id = uuid(), isHead = false) }
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            current.copy(statements = insertBlocksAfterBlock(stmts, targetChainId, clones, afterBlockId))
        }
    }

    private fun insertBlocksAfterBlock(
        stmts: List<KBStmt>,
        chainId: String,
        blocks: List<KBCallBlock>,
        afterBlockId: String,
    ): List<KBStmt> = stmts.map { stmt ->
        when {
            stmt is KBChainStmt && stmt.id == chainId -> {
                val steps = stmt.steps.toMutableList()
                val idx = steps.indexOfFirst { it is KBCallBlock && it.id == afterBlockId }.takeIf { it >= 0 } ?: (steps.size - 1)
                steps.addAll(idx + 1, blocks)
                stmt.copy(steps = steps.fixHeads())
            }
            stmt is KBChainStmt -> stmt.copy(steps = insertBlocksAfterBlockInItems(stmt.steps, chainId, blocks, afterBlockId))
            stmt is KBLetStmt ->
                stmt.copy(value = stmt.value.inArgs { insertBlocksAfterBlockInArgs(it, chainId, blocks, afterBlockId) })

            stmt is KBConstStmt ->
                stmt.copy(value = stmt.value.inArgs { insertBlocksAfterBlockInArgs(it, chainId, blocks, afterBlockId) } ?: stmt.value)
            else -> stmt
        }
    }

    private fun insertBlocksAfterBlockInItems(
        items: List<KBChainItem>,
        chainId: String,
        blocks: List<KBCallBlock>,
        afterBlockId: String,
    ): List<KBChainItem> = items.map { item ->
        when (item) {
            is KBCallBlock -> item.copy(args = insertBlocksAfterBlockInArgs(item.args, chainId, blocks, afterBlockId))
            else -> item
        }
    }

    private fun insertBlocksAfterBlockInArgs(
        args: List<KBArgValue>,
        chainId: String,
        blocks: List<KBCallBlock>,
        afterBlockId: String,
    ): List<KBArgValue> = args.map { argValue ->
        when {
            argValue is KBNestedChainArg && argValue.chain.id == chainId -> {
                val steps = argValue.chain.steps.toMutableList()
                val idx = steps.indexOfFirst { it is KBCallBlock && it.id == afterBlockId }.takeIf { it >= 0 } ?: (steps.size - 1)
                steps.addAll(idx + 1, blocks)
                argValue.copy(chain = argValue.chain.copy(steps = steps.fixHeads()))
            }

            argValue is KBNestedChainArg ->
                argValue.copy(
                    chain = argValue.chain.copy(
                        steps = insertBlocksAfterBlockInItems(argValue.chain.steps, chainId, blocks, afterBlockId)
                    )
                )

            else -> argValue
        }
    }

    private fun moveBlocksToSlot(blocks: List<KBCallBlock>, blockId: String, slotIndex: Int) {
        val clones = blocks.map { it.copy(id = uuid()) }
        update { current ->
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            val newChain = buildSlotDropChain(clones)
            current.copy(
                statements = updateBlockInStmts(stmts, blockId, slotIndex, KBNestedChainArg(newChain))
            )
        }
    }

    private fun replaceWithNewBlock(funcName: String, targetBlockId: String) {
        val newBlock = KBCallBlock(id = uuid(), funcName = funcName)
        update { current ->
            current.copy(statements = replaceBlockInStmts(current.statements, targetBlockId, listOf(newBlock)))
        }
    }

    private fun replaceWithMovedBlocks(blocks: List<KBCallBlock>, targetBlockId: String) {
        if (blocks.any { it.id == targetBlockId }) return   // self-replace guard
        val clones = blocks.map { it.copy(id = uuid()) }
        update { current ->
            if (blockContains(blocks, targetBlockId)) return@update current  // cycle guard
            var stmts = current.statements
            for (block in blocks) stmts = removeBlockFromStmts(stmts, block.id)
            current.copy(statements = replaceBlockInStmts(stmts, targetBlockId, clones))
        }
    }

    private fun replaceBlockInStmts(
        stmts: List<KBStmt>, targetBlockId: String, replacements: List<KBCallBlock>,
    ): List<KBStmt> = stmts.map { stmt ->
        when (stmt) {
            is KBChainStmt -> stmt.copy(
                steps = replaceBlockInItems(stmt.steps, targetBlockId, replacements).fixHeads()
            )
            is KBLetStmt -> stmt.copy(value = stmt.value.inArgs { replaceBlockInArgs(it, targetBlockId, replacements) })
            is KBConstStmt -> stmt.copy(value = stmt.value.inArgs { replaceBlockInArgs(it, targetBlockId, replacements) } ?: stmt.value)
            else -> stmt
        }
    }

    private fun replaceBlockInItems(
        items: List<KBChainItem>, targetBlockId: String, replacements: List<KBCallBlock>,
    ): List<KBChainItem> {
        val result = mutableListOf<KBChainItem>()
        for (item in items) {
            when {
                item is KBCallBlock && item.id == targetBlockId -> result.addAll(replacements)
                item is KBCallBlock -> result.add(
                    item.copy(args = replaceBlockInArgs(item.args, targetBlockId, replacements))
                )

                else -> result.add(item)
            }
        }
        return result
    }

    private fun replaceBlockInArgs(
        args: List<KBArgValue>, targetBlockId: String, replacements: List<KBCallBlock>,
    ): List<KBArgValue> = args.map { arg ->
        when (arg) {
            is KBNestedChainArg -> arg.copy(
                chain = arg.chain.copy(
                    steps = replaceBlockInItems(arg.chain.steps, targetBlockId, replacements).fixHeads()
                )
            )

            else -> arg
        }
    }

    /** Returns true if [targetBlockId] is nested inside any of [ancestorBlocks]' argument chains. */
    private fun blockContains(ancestorBlocks: List<KBCallBlock>, targetBlockId: String): Boolean =
        ancestorBlocks.any { blockContainsInArgs(it.args, targetBlockId) }

    private fun blockContainsInArgs(args: List<KBArgValue>, targetId: String): Boolean =
        args.any { arg ->
            arg is KBNestedChainArg && arg.chain.steps.any { item ->
                item is KBCallBlock && (item.id == targetId || blockContainsInArgs(item.args, targetId))
            }
        }

    private fun moveRow(sourceStmtId: String, index: Int) {
        update { current ->
            val stmts = current.statements.toMutableList()
            val srcIndex = stmts.indexOfFirst { it.id == sourceStmtId }
            if (srcIndex < 0) return@update current
            val chain = stmts.removeAt(srcIndex)
            val insertAt = if (index > srcIndex) (index - 1).coerceAtLeast(0) else index
            stmts.add(insertAt.coerceIn(0, stmts.size), chain)
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
            is KBLetStmt -> when {
                stmt.id == blockId && slotIndex == 0 -> stmt.copy(value = arg)
                else -> stmt.copy(value = stmt.value.inArgs { updateBlockInArgs(it, blockId, slotIndex, arg) })
            }

            is KBConstStmt -> when {
                stmt.id == blockId && slotIndex == 0 -> stmt.copy(value = arg)
                else -> stmt.copy(value = stmt.value.inArgs { updateBlockInArgs(it, blockId, slotIndex, arg) } ?: stmt.value)
            }
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
                is KBLetStmt -> stmt.copy(value = stmt.value.inArgs { removeBlockFromArgs(it, blockId) })
                is KBConstStmt -> stmt.copy(value = stmt.value.inArgs { removeBlockFromArgs(it, blockId) } ?: stmt.value)
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
                        val literalHead = newSteps.filterIsInstance<KBStringLiteralItem>().firstOrNull()
                        if (literalHead != null) KBStringArg(literalHead.value) else KBEmptyArg("")
                    } else argValue.copy(chain = argValue.chain.copy(steps = newSteps))
                }

                else -> argValue
            }
        }

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
            stmt is KBLetStmt ->
                stmt.copy(value = stmt.value.inArgs { insertBlockIntoChainInArgs(it, chainId, block, insertBeforeBlockId) })

            stmt is KBConstStmt ->
                stmt.copy(value = stmt.value.inArgs { insertBlockIntoChainInArgs(it, chainId, block, insertBeforeBlockId) } ?: stmt.value)
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

    private fun appendBlockToChain(stmts: List<KBStmt>, chainId: String, block: KBCallBlock): List<KBStmt> =
        stmts.map { stmt ->
            when {
                stmt is KBChainStmt && stmt.id == chainId ->
                    stmt.copy(steps = (stmt.steps + block).fixHeads())
                stmt is KBChainStmt ->
                    stmt.copy(steps = appendBlockToChainInItems(stmt.steps, chainId, block))
                stmt is KBLetStmt ->
                    stmt.copy(value = stmt.value.inArgs { appendBlockToChainInArgs(it, chainId, block) })

                stmt is KBConstStmt ->
                    stmt.copy(value = stmt.value.inArgs { appendBlockToChainInArgs(it, chainId, block) } ?: stmt.value)
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

    private fun buildSlotDropChain(newBlocks: List<KBCallBlock>): KBChainStmt {
        val steps = newBlocks.mapIndexed { i, b -> b.copy(isHead = i == 0) }
        return KBChainStmt(id = uuid(), steps = steps)
    }

    private fun updateSlotDropInStmts(
        stmts: List<KBStmt>, blockId: String, slotIndex: Int, newBlocks: List<KBCallBlock>,
    ): List<KBStmt> = stmts.map { stmt ->
        when (stmt) {
            is KBChainStmt -> stmt.copy(steps = updateSlotDropInItems(stmt.steps, blockId, slotIndex, newBlocks))
            is KBLetStmt -> when {
                stmt.id == blockId && slotIndex == 0 -> stmt.copy(value = KBNestedChainArg(buildSlotDropChain(newBlocks)))
                else -> stmt.copy(value = stmt.value.inArgs { updateSlotDropInArgs(it, blockId, slotIndex, newBlocks) })
            }

            is KBConstStmt -> when {
                stmt.id == blockId && slotIndex == 0 -> stmt.copy(value = KBNestedChainArg(buildSlotDropChain(newBlocks)))
                else -> stmt.copy(value = stmt.value.inArgs { updateSlotDropInArgs(it, blockId, slotIndex, newBlocks) } ?: stmt.value)
            }
            else -> stmt
        }
    }

    private fun updateSlotDropInItems(
        items: List<KBChainItem>, blockId: String, slotIndex: Int, newBlocks: List<KBCallBlock>,
    ): List<KBChainItem> = items.map { item ->
        when {
            item is KBCallBlock && item.id == blockId -> {
                val newChain = buildSlotDropChain(newBlocks)
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

    // ---- String literal item helpers ----------------------------------------

    private fun updateStringLiteralItemInStmts(stmts: List<KBStmt>, chainId: String, newValue: String): List<KBStmt> =
        stmts.map { stmt ->
            when (stmt) {
                is KBChainStmt -> if (stmt.id == chainId)
                    stmt.copy(steps = applyStringLiteralChange(stmt.steps, newValue))
                else
                    stmt.copy(steps = updateStringLiteralItemInItems(stmt.steps, chainId, newValue))
                is KBLetStmt ->
                    stmt.copy(value = stmt.value.inArgs { updateStringLiteralItemInArgs(it, chainId, newValue) })

                is KBConstStmt ->
                    stmt.copy(value = stmt.value.inArgs { updateStringLiteralItemInArgs(it, chainId, newValue) } ?: stmt.value)
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

    private fun applyStringLiteralChange(steps: List<KBChainItem>, newValue: String): List<KBChainItem> {
        if (steps.firstOrNull() !is KBStringLiteralItem) return steps
        return if (newValue.isEmpty()) steps.drop(1).fixHeads()
        else listOf(KBStringLiteralItem(newValue)) + steps.drop(1)
    }

    // ---- Layout / newline helpers -------------------------------------------

    private fun toggleLayoutInStmts(stmts: List<KBStmt>, blockId: String): List<KBStmt> =
        stmts.map { stmt ->
            when (stmt) {
                is KBChainStmt -> stmt.copy(steps = toggleLayoutInItems(stmt.steps, blockId))
                is KBLetStmt -> stmt.copy(value = stmt.value.inArgs { toggleLayoutInArgs(it, blockId) })
                is KBConstStmt -> stmt.copy(value = stmt.value.inArgs { toggleLayoutInArgs(it, blockId) } ?: stmt.value)
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

    private fun toggleNewlineInStmts(stmts: List<KBStmt>, chainId: String, blockId: String): List<KBStmt> =
        stmts.map { stmt ->
            when {
                stmt is KBChainStmt && stmt.id == chainId ->
                    stmt.copy(steps = toggleNewlineBeforeBlockInSteps(stmt.steps, blockId))
                stmt is KBChainStmt ->
                    stmt.copy(steps = toggleNewlineInItems(stmt.steps, chainId, blockId))
                stmt is KBLetStmt ->
                    stmt.copy(value = stmt.value.inArgs { toggleNewlineInArgs(it, chainId, blockId) })

                stmt is KBConstStmt ->
                    stmt.copy(value = stmt.value.inArgs { toggleNewlineInArgs(it, chainId, blockId) } ?: stmt.value)
                else -> stmt
            }
        }

    private fun toggleNewlineInItems(items: List<KBChainItem>, chainId: String, blockId: String): List<KBChainItem> =
        items.map { item ->
            when (item) {
                is KBCallBlock -> item.copy(args = toggleNewlineInArgs(item.args, chainId, blockId))
                else -> item
            }
        }

    private fun toggleNewlineInArgs(args: List<KBArgValue>, chainId: String, blockId: String): List<KBArgValue> =
        args.map { argValue ->
            when (argValue) {
                is KBNestedChainArg -> when {
                    argValue.chain.id == chainId -> argValue.copy(
                        chain = argValue.chain.copy(
                            steps = toggleNewlineBeforeBlockInSteps(argValue.chain.steps, blockId)
                        )
                    )

                    else -> argValue.copy(
                        chain = argValue.chain.copy(
                            steps = toggleNewlineInItems(argValue.chain.steps, chainId, blockId)
                        )
                    )
                }

                else -> argValue
            }
        }

    private fun toggleNewlineBeforeBlockInSteps(steps: List<KBChainItem>, blockId: String): List<KBChainItem> {
        val idx = steps.indexOfFirst { it is KBCallBlock && it.id == blockId }
        if (idx < 0) return steps
        val result = steps.toMutableList()
        if (idx > 0 && result[idx - 1] is KBNewlineHint) {
            result.removeAt(idx - 1)
        } else {
            result.add(idx, KBNewlineHint)
        }
        return result
    }

    // ---- Utility ------------------------------------------------------------

    /**
     * Applies an InArgs-style transform to a single [KBArgValue], returning the
     * transformed value, or null when this is null.  Used to recurse into
     * [KBLetStmt.value] / [KBConstStmt.value] without duplicating traversal code.
     */
    private fun KBArgValue?.inArgs(transform: (List<KBArgValue>) -> List<KBArgValue>): KBArgValue? =
        if (this == null) null else transform(listOf(this)).firstOrNull()

    private fun List<KBChainItem>.fixHeads(): List<KBChainItem> {
        var isFirst = true
        return map { item ->
            if (item !is KBCallBlock) item
            else if (isFirst) {
                isFirst = false; item.copy(isHead = true)
            } else item.copy(isHead = false)
        }
    }
}
