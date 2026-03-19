package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.addons.styling.StyleSheets
import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseMove
import de.peekandpoke.ultra.html.onMouseUp
import de.peekandpoke.ultra.streams.Stream
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangKeyBindings
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@Suppress("FunctionName")
fun Tag.KlangBlocksEditorComp(
    availableLibraries: List<KlangScriptLibrary>,
    initialCode: String = "",
    onCodeChanged: (String) -> Unit,
    onCodeGenChanged: ((CodeGenResult) -> Unit)? = null,
    highlights: Stream<KlangBlocksHighlightBuffer.HighlightSignal?>,
    theme: KlangBlocksTheme = KlangBlocksTheme.Default,
    hoverPopup: HoverPopupCtrl? = null,
    hoverContent: (FlowContent.(KlangSymbol) -> Unit)? = null,
) = comp(
    KlangBlocksEditorComp.Props(
        availableLibraries = availableLibraries,
        initialCode = initialCode,
        onCodeChanged = onCodeChanged,
        onCodeGenChanged = onCodeGenChanged,
        highlights = highlights,
        theme = theme,
        hoverPopup = hoverPopup,
        hoverContent = hoverContent,
    )
) {
    KlangBlocksEditorComp(it)
}

class KlangBlocksEditorComp(ctx: Ctx<Props>) : Component<KlangBlocksEditorComp.Props>(ctx) {

    data class Props(
        val availableLibraries: List<KlangScriptLibrary>,
        val initialCode: String = "",
        val onCodeChanged: (String) -> Unit,
        val onCodeGenChanged: ((CodeGenResult) -> Unit)? = null,
        val highlights: Stream<KlangBlocksHighlightBuffer.HighlightSignal?>,
        val theme: KlangBlocksTheme = KlangBlocksTheme.Default,
        val hoverPopup: HoverPopupCtrl? = null,
        val hoverContent: (FlowContent.(KlangSymbol) -> Unit)? = null,
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
            val stmt: KBStmt,
            val ghostX: Double,
            val ghostY: Double,
        ) : DragState()

        /**
         * Dragging a block from any chain (top-level or nested).
         * [ctrlHeld] = false → single block; true → block + all following (tail).
         */
        data class DraggingBlock(
            val sourceChainId: String,
            val sourceChain: KBChainStmt,
            val block: KBCallBlock,
            val ctrlHeld: Boolean,
            val ghostX: Double,
            val ghostY: Double,
        ) : DragState()
    }

    // ---- Component state -------------------------------------------

    private val editingCtx = KBProgramEditingCtx(
        initialProgram = parseCode(props.initialCode),
        onChanged = { newProgram ->
            val result = newProgram.toCodeGen()
            props.onCodeChanged(result.code)
            props.onCodeGenChanged?.invoke(result)
            triggerRedraw()
        },
    ).also {
        // Update the host component (needed so that highlight are displayed correctly from the start)
        val gen = it.program.toCodeGen()
        props.onCodeChanged(gen.code)
        props.onCodeGenChanged?.invoke(it.program.toCodeGen())
    }

    fun setCode(code: String) {
        editingCtx.update {
            parseCode(code)
        }
    }

    private var dragState: DragState by value(DragState.None)

    private val keydownListener: (Event) -> Unit = listener@{ event ->
        val ke = event as? KeyboardEvent ?: return@listener

        // Escape cancels any active drag.
        if (ke.key == "Escape" && dragState != DragState.None) {
            dragState = DragState.None
            return@listener
        }

        // CTRL held during a block drag → switch to tail mode
        if (ke.key == "Control") {
            val ds = dragState
            if (ds is DragState.DraggingBlock && !ds.ctrlHeld) dragState = ds.copy(ctrlHeld = true)
            return@listener
        }

        if (!ke.ctrlKey && !ke.metaKey) {
            return@listener
        }

        when {
            KlangKeyBindings.isUndo(ke) -> {
                ke.preventDefault(); editingCtx.undo()
            }

            KlangKeyBindings.isRedo(ke) -> {
                ke.preventDefault(); editingCtx.redo()
            }
        }
    }

    private val keyupListener: (Event) -> Unit = listener@{ event ->
        val ke = event as? KeyboardEvent ?: return@listener
        // CTRL released during a block drag → revert to single-block mode
        if (ke.key == "Control") {
            val ds = dragState
            if (ds is DragState.DraggingBlock && ds.ctrlHeld) dragState = ds.copy(ctrlHeld = false)
        }
    }

    private val contextmenuListener: (Event) -> Unit = listener@{ event ->
        if (dragState == DragState.None) return@listener
        event.preventDefault()
        dragState = DragState.None
    }

    init {
        lifecycle {
            onMount {
                StyleSheets.mount(props.theme.styles)
                props.onCodeGenChanged?.invoke(editingCtx.program.toCodeGen())
                document.addEventListener("keydown", keydownListener)
                document.addEventListener("keyup", keyupListener)
                document.addEventListener("contextmenu", contextmenuListener)
            }
            onUnmount {
                StyleSheets.unmount(props.theme.styles)
                document.removeEventListener("keydown", keydownListener)
                document.removeEventListener("keyup", keyupListener)
                document.removeEventListener("contextmenu", contextmenuListener)
            }
        }
    }

    private val canvasDivId = "kb-canvas-${hashCode()}"

    // ---- Derived ---------------------------------------------------

    /** Builds the DndState passed to all children; null when nothing is dragging. */
    private fun buildDndState(): DndState? = when (val ds = dragState) {
        is DragState.DraggingFromPalette -> DndState(
            ghostX = ds.ghostX,
            ghostY = ds.ghostY,
            ghostLabel = ds.funcName,
            ghostWidth = estimateGhostWidth(ds.funcName),
            targets = setOf(
                DropTarget.RowGap, DropTarget.ChainEnd, DropTarget.ChainInsert,
                DropTarget.EmptySlot, DropTarget.ReplaceBlock,
            ),
            onDrop = { dest ->
                editingCtx.execute(DropAction.CreateBlock(ds.funcName, dest))
                dragState = DragState.None
            },
            sourceChainId = null,
        )

        is DragState.DraggingFromCanvas -> {
            val label = ds.stmt.canvasGhostLabel()
            DndState(
                ghostX = ds.ghostX,
                ghostY = ds.ghostY,
                ghostLabel = label,
                ghostWidth = estimateGhostWidth(label),
                targets = setOf(DropTarget.RowGap),
                onDrop = { dest ->
                    editingCtx.execute(DropAction.MoveRow(ds.stmtId, (dest as DropDestination.RowGap).index))
                    dragState = DragState.None
                },
                sourceChainId = ds.stmtId,
            )
        }

        is DragState.DraggingBlock -> {
            // Compute the list of blocks to drag: single block or tail (block + all following)
            val allBlocks = ds.sourceChain.steps.filterIsInstance<KBCallBlock>()
            val fromIdx = allBlocks.indexOfFirst { it.id == ds.block.id }
            val blocks = if (ds.ctrlHeld && fromIdx >= 0) allBlocks.drop(fromIdx) else listOf(ds.block)
            val label = blocks.joinToString(".") { it.funcName }
            DndState(
                ghostX = ds.ghostX,
                ghostY = ds.ghostY,
                ghostLabel = label,
                ghostWidth = estimateGhostWidth(label),
                targets = setOf(
                    DropTarget.RowGap, DropTarget.ChainEnd, DropTarget.ChainInsert,
                    DropTarget.EmptySlot, DropTarget.ReplaceBlock,
                ),
                onDrop = { dest ->
                    editingCtx.execute(DropAction.MoveBlocks(blocks, dest))
                    dragState = DragState.None
                },
                sourceChainId = ds.sourceChainId,
            )
        }

        else -> null
    }

    /** Estimates the pixel width of the drag ghost element (monospace 13px, 10px horizontal padding). */
    private fun estimateGhostWidth(label: String): Double = label.length * 7.8 + 20.0

    private fun KBStmt.canvasGhostLabel(): String = when (this) {
        is KBChainStmt -> steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName }
        is KBLetStmt -> "let $name"
        is KBConstStmt -> "const $name"
        else -> id
    }

    // ---- Init helpers ----------------------------------------------

    private fun parseCode(code: String): KBProgram {
        val src = code.trim()
        if (src.isEmpty()) return KBProgram()
        return try {
            AstToKBlocks.convert(KlangScriptParser.parse(src))
        } catch (e: Exception) {
            console.error("Error parsing initial code:", e)
            KBProgram()
        }
    }

    // ---- Drag event handlers ---------------------------------------

    private fun onPaletteDragStart(funcName: String, x: Double, y: Double) {
        dragState = DragState.DraggingFromPalette(funcName, x, y)
    }

    private fun onCanvasDragStart(stmtId: String, stmt: KBStmt, x: Double, y: Double) {
        dragState = DragState.DraggingFromCanvas(stmtId, stmt, x, y)
    }

    private fun onBlockDragStart(
        sourceChainId: String, sourceChain: KBChainStmt,
        block: KBCallBlock, ctrlHeld: Boolean, x: Double, y: Double,
    ) {
        dragState = DragState.DraggingBlock(sourceChainId, sourceChain, block, ctrlHeld, x, y)
    }

    private fun onMouseMoved(x: Double, y: Double) {
        dragState = when (val current = dragState) {
            is DragState.DraggingFromPalette -> current.copy(ghostX = x, ghostY = y)
            is DragState.DraggingFromCanvas -> current.copy(ghostX = x, ghostY = y)
            is DragState.DraggingBlock -> current.copy(ghostX = x, ghostY = y)
            else -> return
        }
    }

    /** Mouse released outside any drop target — cancel the drag. */
    private fun onMouseReleased() {
        dragState = DragState.None
    }

    // ---- Render ----------------------------------------------------

    override fun VDom.render() {
        val dndState = buildDndState()
        val ctx = KlangBlocksCtx(
            editing = editingCtx,
            highlights = props.highlights,
            dnd = DndCtrl(
                state = dndState,
                startPaletteDrag = DndCtrl.PaletteDragStarter(::onPaletteDragStart),
                startCanvasDrag = DndCtrl.CanvasDragStarter(::onCanvasDragStart),
                startBlockDrag = DndCtrl.BlockDragStarter(::onBlockDragStart),
            ),
            theme = props.theme,
            hoverPopup = props.hoverPopup,
            hoverContent = props.hoverContent,
        )

        div(classes = props.theme.styles.editorRoot()) {
            onMouseMove { event ->
                onMouseMoved(event.clientX.toDouble(), event.clientY.toDouble())
            }
            onMouseUp {
                onMouseReleased()
            }

            // Palette
            KlangBlocksPaletteComp(
                availableLibraries = props.availableLibraries,
                ctx = ctx,
            )

            // Canvas
            KlangBlocksCanvasComp(
                ctx = ctx,
                canvasDivId = canvasDivId,
            )

            // Drag ghost — follows cursor during any drag
            if (dndState != null) {
                div(classes = props.theme.styles.dragGhostFixed()) {
                    css {
                        left = dndState.ghostX.px
                        top = dndState.ghostY.px
                    }
                    span(classes = props.theme.styles.dragGhostLabel()) {
                        css { backgroundColor = Color(props.theme.blockColor(null)) }
                        +dndState.ghostLabel
                    }
                }
            }
        }
    }
}
