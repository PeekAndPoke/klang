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
import kotlinx.browser.document
import kotlinx.css.*
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

        data class DraggingNestedBlock(
            val block: KBCallBlock,
            val ghostX: Double,
            val ghostY: Double,
        ) : DragState()
    }

    // ---- Component state -------------------------------------------

    private val editingCtx = KBProgramEditingCtx(
        initialProgram = parseInitialCode(),
        onChanged = { newProgram ->
            props.onCodeChanged(newProgram.toCode())
            triggerRedraw()
        },
    )

    private var dragState: DragState by value(DragState.None)

    private val keydownListener: (Event) -> Unit = listener@{ event ->
        val ke = event as? KeyboardEvent ?: return@listener
        // Let the browser handle undo/redo inside text inputs
        val tag = ke.target?.asDynamic()?.tagName?.toString()?.uppercase() ?: ""
        if (tag == "INPUT" || tag == "TEXTAREA") return@listener
        if (!ke.ctrlKey && !ke.metaKey) return@listener
        when {
            ke.key == "z" && !ke.shiftKey -> {
                ke.preventDefault(); editingCtx.undo()
            }

            ke.key == "z" && ke.shiftKey -> {
                ke.preventDefault(); editingCtx.redo()
            }

            ke.key == "y" -> {
                ke.preventDefault(); editingCtx.redo()
            }
        }
    }

    init {
        lifecycle {
            onMount { document.addEventListener("keydown", keydownListener) }
            onUnmount { document.removeEventListener("keydown", keydownListener) }
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
            onDropToPosition = { index ->
                editingCtx.commitPaletteDropAtPosition(ds.funcName, index)
                dragState = DragState.None
            },
            onDropToChain = { chainId ->
                editingCtx.commitChainAppend(chainId, ds.funcName)
                dragState = DragState.None
            },
            onDropToSlot = { stmtId, blockId, slotIdx ->
                editingCtx.commitPaletteDropToSlot(ds.funcName, stmtId, blockId, slotIdx)
                dragState = DragState.None
            },
        )

        is DragState.DraggingFromCanvas -> DndState(
            ghostX = ds.ghostX,
            ghostY = ds.ghostY,
            ghostLabel = ds.chain.steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName },
            onDropToPosition = { index ->
                editingCtx.commitMoveToPosition(ds.stmtId, index)
                dragState = DragState.None
            },
            onDropToChain = { chainId ->
                editingCtx.commitCanvasChainAppend(ds.stmtId, ds.chain, chainId)
                dragState = DragState.None
            },
            onDropToSlot = { stmtId, blockId, slotIdx ->
                editingCtx.commitCanvasSlotDrop(ds.stmtId, ds.chain, stmtId, blockId, slotIdx)
                dragState = DragState.None
            },
        )

        is DragState.DraggingNestedBlock -> DndState(
            ghostX = ds.ghostX,
            ghostY = ds.ghostY,
            ghostLabel = ds.block.funcName,
            onDropToPosition = { index ->
                editingCtx.commitNestedBlockDragToPosition(ds.block, index)
                dragState = DragState.None
            },
            onDropToChain = { chainId ->
                editingCtx.commitNestedBlockDragToChain(ds.block, chainId)
                dragState = DragState.None
            },
            onDropToSlot = { _, blockId, slotIdx ->
                editingCtx.commitNestedBlockDragToSlot(ds.block, blockId, slotIdx)
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
            console.error("Error parsing initial code:", e)
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

    private fun onNestedBlockDragStart(block: KBCallBlock, x: Double, y: Double) {
        dragState = DragState.DraggingNestedBlock(block, x, y)
    }

    private fun onMouseMoved(x: Double, y: Double) {
        dragState = when (val current = dragState) {
            is DragState.DraggingFromPalette -> current.copy(ghostX = x, ghostY = y)
            is DragState.DraggingFromCanvas -> current.copy(ghostX = x, ghostY = y)
            is DragState.DraggingNestedBlock -> current.copy(ghostX = x, ghostY = y)
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
            dnd = DndCtrl(
                state = dndState,
                startPaletteDrag = ::onPaletteDragStart,
                startCanvasDrag = ::onCanvasDragStart,
                startNestedBlockDrag = ::onNestedBlockDragStart,
            ),
        )

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
                ctx = ctx,
            )

            // Canvas
            KlangBlocksCanvasComp(
                ctx = ctx,
                canvasDivId = canvasDivId,
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
