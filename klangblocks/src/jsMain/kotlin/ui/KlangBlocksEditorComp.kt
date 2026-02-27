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
import org.w3c.dom.HTMLElement

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

    // ---- Drag state ------------------------------------------------

    sealed class DragState {
        object None : DragState()

        data class DraggingFromPalette(
            val funcName: String,
            val ghostX: Double,
            val ghostY: Double,
            /** ID of the KBChainStmt the cursor is hovering over for chaining, or null. */
            val hoveredChainId: String? = null,
        ) : DragState()

        /** Dragging an existing canvas row into a compatible block slot. */
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

    // ---- Derived -------------------------------------------------------

    private val importedLibraryNames: Set<String>
        get() = program.statements.filterIsInstance<KBImportStmt>().map { it.libraryName }.toSet()

    private val isDraggingFromPalette: Boolean
        get() = dragState is DragState.DraggingFromPalette

    private val isDraggingFromCanvas: Boolean
        get() = dragState is DragState.DraggingFromCanvas

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

    // ---- Drag logic ------------------------------------------------

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

    private fun onMouseReleased(x: Double, y: Double) {
        when (val current = dragState) {
            is DragState.DraggingFromPalette -> {
                if (isInsideCanvas(x, y)) {
                    val chainId = current.hoveredChainId
                    if (chainId != null) commitChainDrop(chainId, current.funcName)
                    else commitDrop(current.funcName)
                }
                dragState = DragState.None
            }

            // Canvas drag released without landing on a compatible slot → cancel
            is DragState.DraggingFromCanvas -> dragState = DragState.None

            else -> {}
        }
    }

    private fun onChainHoverStart(chainId: String) {
        val current = dragState
        if (current is DragState.DraggingFromPalette) {
            dragState = current.copy(hoveredChainId = chainId)
        }
    }

    private fun onChainHoverEnd() {
        val current = dragState
        if (current is DragState.DraggingFromPalette && current.hoveredChainId != null) {
            dragState = current.copy(hoveredChainId = null)
        }
    }

    private fun isInsideCanvas(x: Double, y: Double): Boolean {
        val el = dom?.querySelector("#$canvasDivId") as? HTMLElement ?: return false
        val rect = el.getBoundingClientRect()
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }

    // ---- Program mutations -----------------------------------------

    /** Drop palette block as a new standalone chain row. */
    private fun commitDrop(funcName: String) {
        val chain = KBChainStmt(
            id = uuid(),
            steps = listOf(KBCallBlock(id = uuid(), funcName = funcName, isHead = true))
        )
        program = program.copy(statements = program.statements + chain)
    }

    /** Append a new call block to an existing chain (palette drag onto chain drop zone). */
    private fun commitChainDrop(chainId: String, funcName: String) {
        program = program.copy(
            statements = program.statements.map { stmt ->
                if (stmt.id != chainId || stmt !is KBChainStmt) stmt
                else stmt.copy(
                    steps = stmt.steps + KBCallBlock(id = uuid(), funcName = funcName, isHead = false)
                )
            }
        )
    }

    /**
     * Drop a canvas chain into a slot: removes the source row and sets the slot value
     * to KBNestedChainArg wrapping the source chain.
     */
    private fun commitCanvasSlotDrop(
        sourceStmtId: String,
        sourceChain: KBChainStmt,
        targetStmtId: String,
        blockId: String,
        slotIndex: Int,
    ) {
        if (sourceStmtId == targetStmtId) return  // prevent self-nesting
        program = program.copy(
            statements = program.statements.mapNotNull { stmt ->
                when {
                    stmt.id == sourceStmtId -> null  // remove source row
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

    /** Called by the canvas when a canvas-drag is released onto a compatible slot. */
    private fun onCanvasSlotDrop(targetStmtId: String, blockId: String, slotIndex: Int) {
        val current = dragState as? DragState.DraggingFromCanvas ?: return
        commitCanvasSlotDrop(current.stmtId, current.chain, targetStmtId, blockId, slotIndex)
        dragState = DragState.None
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

    /** Remove an entire statement (import / let / const). */
    private fun onRemoveStmt(stmtId: String) {
        program = program.copy(
            statements = program.statements.filter { it.id != stmtId }
        )
    }

    // ---- Render ----------------------------------------------------

    override fun VDom.render() {
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
            onMouseUp { event ->
                onMouseReleased(event.clientX.toDouble(), event.clientY.toDouble())
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
                isDraggingFromPalette = isDraggingFromPalette,
                isDraggingFromCanvas = isDraggingFromCanvas,
                onArgChanged = ::onArgChanged,
                onRemoveBlock = ::onRemoveBlock,
                onRemoveStmt = ::onRemoveStmt,
                onChainHoverStart = ::onChainHoverStart,
                onChainHoverEnd = ::onChainHoverEnd,
                onCanvasDragStart = ::onCanvasDragStart,
                onCanvasSlotDrop = ::onCanvasSlotDrop,
            )

            // Drag ghost — follows cursor during any drag
            val ds = dragState
            val (ghostX, ghostY, ghostLabel) = when (ds) {
                is DragState.DraggingFromPalette -> Triple(ds.ghostX, ds.ghostY, ds.funcName)
                is DragState.DraggingFromCanvas -> Triple(
                    ds.ghostX, ds.ghostY,
                    ds.chain.steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName }
                )

                else -> return@div
            }

            div {
                css {
                    position = Position.fixed
                    left = ghostX.px
                    top = ghostY.px
                    put("pointer-events", "none")
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
                    +ghostLabel
                }
            }
        }
    }
}
