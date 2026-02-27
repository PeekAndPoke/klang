package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseMove
import de.peekandpoke.ultra.html.onMouseUp
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksEditorComp(
    availableLibraries: List<KlangScriptLibrary>,
    initialCode: String = "",
    onCodeChanged: (String) -> Unit,
) = comp(
    KlangBlocksEditorComp.Props(
        availableLibraries = availableLibraries,
        initialCode = initialCode,
        onCodeChanged = onCodeChanged,
    )
) {
    KlangBlocksEditorComp(it)
}

class KlangBlocksEditorComp(ctx: Ctx<Props>) : Component<KlangBlocksEditorComp.Props>(ctx) {

    data class Props(
        val availableLibraries: List<KlangScriptLibrary>,
        val initialCode: String = "",
        val onCodeChanged: (String) -> Unit,
    )

    // ---- Drag state FSM --------------------------------------------

    sealed class DragState {
        object None : DragState()

        data class DraggingFromPalette(
            val funcName: String,
            val ghostX: Double,
            val ghostY: Double,
        ) : DragState()

        data class DraggingFromCanvas(
            val stmtId: String,
            val chain: KBChainStmt,
            val ghostX: Double,
            val ghostY: Double,
        ) : DragState()
    }

    // ---- Component state -------------------------------------------

    private var program: KBProgram by value(parseInitialCode()) { props.onCodeChanged(it.toCode()) }
    private var dragState: DragState by value(DragState.None)

    private val canvasDivId = "kb-canvas-${hashCode()}"

    // ---- Derived ---------------------------------------------------

    private val importedLibraryNames: Set<String>
        get() = program.statements.filterIsInstance<KBImportStmt>().map { it.libraryName }.toSet()

    /** Builds the DndState passed to all children; null when nothing is dragging. */
    private fun buildDndState(): DndState? = when (val ds = dragState) {
        is DragState.DraggingFromPalette -> DndState(
            ghostX = ds.ghostX,
            ghostY = ds.ghostY,
            ghostLabel = ds.funcName,
            onDropToPosition = { index ->
                commitPaletteDropAtPosition(ds.funcName, index)
                dragState = DragState.None
            },
            onDropToChain = { chainId ->
                commitChainAppend(chainId, ds.funcName)
                dragState = DragState.None
            },
            onDropToSlot = null,
        )

        is DragState.DraggingFromCanvas -> DndState(
            ghostX = ds.ghostX,
            ghostY = ds.ghostY,
            ghostLabel = ds.chain.steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName },
            onDropToPosition = { index ->
                commitMoveToPosition(ds.stmtId, index)
                dragState = DragState.None
            },
            onDropToChain = { chainId ->
                commitCanvasChainAppend(ds.stmtId, ds.chain, chainId)
                dragState = DragState.None
            },
            onDropToSlot = { stmtId, blockId, slotIdx ->
                commitCanvasSlotDrop(ds.stmtId, ds.chain, stmtId, blockId, slotIdx)
                dragState = DragState.None
            },
        )

        else -> null
    }

    // ---- Init helpers ----------------------------------------------

    private fun parseInitialCode(): KBProgram {
        val src = props.initialCode.trim()
        if (src.isEmpty()) return KBProgram()
        return try {
            AstToKBlocks.convert(KlangScriptParser.parse(src))
        } catch (e: Exception) {
            KBProgram()
        }
    }

    // ---- Drag event handlers ---------------------------------------

    private fun onPaletteDragStart(funcName: String, x: Double, y: Double) {
        dragState = DragState.DraggingFromPalette(funcName, x, y)
    }

    private fun onCanvasDragStart(stmtId: String, chain: KBChainStmt, x: Double, y: Double) {
        dragState = DragState.DraggingFromCanvas(stmtId, chain, x, y)
    }

    private fun onMouseMoved(x: Double, y: Double) {
        dragState = when (val current = dragState) {
            is DragState.DraggingFromPalette -> current.copy(ghostX = x, ghostY = y)
            is DragState.DraggingFromCanvas -> current.copy(ghostX = x, ghostY = y)
            else -> return
        }
    }

    /** Mouse released outside any drop target — cancel the drag. */
    private fun onMouseReleased() {
        dragState = DragState.None
    }

    // ---- Program mutations -----------------------------------------

    /** Insert a new single-block chain at a specific row index. */
    private fun commitPaletteDropAtPosition(funcName: String, index: Int) {
        val chain = KBChainStmt(
            id = uuid(),
            steps = listOf(KBCallBlock(id = uuid(), funcName = funcName, isHead = true))
        )
        val stmts = program.statements.toMutableList()
        stmts.add(index.coerceIn(0, stmts.size), chain)
        program = program.copy(statements = stmts)
    }

    /** Move an existing canvas chain to a new row index. */
    private fun commitMoveToPosition(stmtId: String, index: Int) {
        val stmts = program.statements.toMutableList()
        val srcIndex = stmts.indexOfFirst { it.id == stmtId }
        if (srcIndex < 0) return
        val chain = stmts.removeAt(srcIndex)
        val insertAt = if (index > srcIndex) (index - 1).coerceAtLeast(0) else index
        stmts.add(insertAt.coerceIn(0, stmts.size), chain)
        program = program.copy(statements = stmts)
    }

    /** Append a palette block to an existing chain. */
    private fun commitChainAppend(chainId: String, funcName: String) {
        program = program.copy(
            statements = program.statements.map { stmt ->
                if (stmt.id != chainId || stmt !is KBChainStmt) stmt
                else stmt.copy(
                    steps = stmt.steps + KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
                )
            }
        )
    }

    /** Append a canvas chain's blocks onto another chain, removing the source row. */
    private fun commitCanvasChainAppend(sourceStmtId: String, sourceChain: KBChainStmt, targetChainId: String) {
        if (sourceStmtId == targetChainId) return
        val sourceBlocks = sourceChain.steps.filterIsInstance<KBCallBlock>().map { it.copy(isHead = false) }
        program = program.copy(
            statements = program.statements.mapNotNull { stmt ->
                when {
                    stmt.id == sourceStmtId -> null
                    stmt.id == targetChainId && stmt is KBChainStmt -> stmt.copy(steps = stmt.steps + sourceBlocks)
                    else -> stmt
                }
            }
        )
    }

    /** Drop a canvas chain into a block slot; removes the source row. */
    private fun commitCanvasSlotDrop(
        sourceStmtId: String,
        sourceChain: KBChainStmt,
        targetStmtId: String,
        blockId: String,
        slotIndex: Int,
    ) {
        if (sourceStmtId == targetStmtId) return
        program = program.copy(
            statements = program.statements.mapNotNull { stmt ->
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

    private fun commitImportLibrary(libraryName: String) {
        val import = KBImportStmt(id = uuid(), libraryName = libraryName)
        program = program.copy(statements = listOf(import) + program.statements)
    }

    private fun onArgChanged(stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) {
        program = program.copy(
            statements = program.statements.map { stmt ->
                if (stmt.id != stmtId) stmt
                else when (stmt) {
                    is KBChainStmt -> stmt.copy(
                        steps = stmt.steps.map { item ->
                            if (item is KBCallBlock && item.id == blockId) {
                                val newArgs = item.args.toMutableList()
                                while (newArgs.size <= slotIndex) newArgs.add(KBEmptyArg(""))
                                newArgs[slotIndex] = arg
                                item.copy(args = newArgs.toList())
                            } else item
                        }
                    )
                    else -> stmt
                }
            }
        )
    }

    /** Remove a single call block; auto-removes the chain if it becomes empty. */
    private fun onRemoveBlock(stmtId: String, blockId: String) {
        program = program.copy(
            statements = program.statements.mapNotNull { stmt ->
                if (stmt.id != stmtId) return@mapNotNull stmt
                when (stmt) {
                    is KBChainStmt -> {
                        val newSteps = stmt.steps.filter { !(it is KBCallBlock && it.id == blockId) }
                        if (newSteps.filterIsInstance<KBCallBlock>().isEmpty()) null
                        else stmt.copy(steps = newSteps)
                    }
                    else -> null
                }
            }
        )
    }

    /** Remove an entire statement (import / let / const / blank line). */
    private fun onRemoveStmt(stmtId: String) {
        program = program.copy(
            statements = program.statements.filter { it.id != stmtId }
        )
    }

    /** Insert a blank line at the given row index. */
    private fun insertBlankLine(index: Int) {
        val blank = KBBlankLine(id = uuid())
        val stmts = program.statements.toMutableList()
        stmts.add(index.coerceIn(0, stmts.size), blank)
        program = program.copy(statements = stmts)
    }

    // ---- Render ----------------------------------------------------

    override fun VDom.render() {
        val dndState = buildDndState()

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                width = 100.pct
                height = 100.pct
                position = Position.relative
                overflow = Overflow.hidden
                backgroundColor = Color("#1e1e2e")
            }

            onMouseMove { event ->
                onMouseMoved(event.clientX.toDouble(), event.clientY.toDouble())
            }
            onMouseUp {
                onMouseReleased()
            }

            // Palette
            KlangBlocksPaletteComp(
                availableLibraries = props.availableLibraries,
                importedLibraryNames = importedLibraryNames,
                onImportLibrary = ::commitImportLibrary,
                onDragStart = ::onPaletteDragStart,
            )

            // Canvas
            KlangBlocksCanvasComp(
                program = program,
                canvasDivId = canvasDivId,
                dndState = dndState,
                onArgChanged = ::onArgChanged,
                onRemoveBlock = ::onRemoveBlock,
                onRemoveStmt = ::onRemoveStmt,
                onCanvasDragStart = ::onCanvasDragStart,
                onInsertBlankLine = ::insertBlankLine,
            )

            // Drag ghost — follows cursor during any drag
            if (dndState != null) {
                div {
                    css {
                        position = Position.fixed
                        left = dndState.ghostX.px
                        top = dndState.ghostY.px
                        pointerEvents = PointerEvents.none
                        zIndex = 9999
                        opacity = 0.85
                        put("transform", "translate(-50%, -50%)")
                    }
                    span {
                        css {
                            display = Display.inlineBlock
                            backgroundColor = Color(categoryColour(null))
                            color = Color.white
                            borderRadius = 8.px
                            padding = Padding(vertical = 5.px, horizontal = 10.px)
                            fontSize = 13.px
                            fontFamily = "monospace"
                            fontWeight = FontWeight.bold
                            whiteSpace = WhiteSpace.nowrap
                            put("box-shadow", "0 4px 12px rgba(0,0,0,0.4)")
                        }
                        +dndState.ghostLabel
                    }
                }
            }
        }
    }
}
