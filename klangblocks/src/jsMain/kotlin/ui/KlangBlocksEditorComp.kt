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
        ) : DragState()
    }

    // ---- Component state -------------------------------------------

    private var program: KBProgram by value(parseInitialCode()) { props.onCodeChanged(it.toCode()) }
    private var dragState: DragState by value(DragState.None)

    private val canvasDivId = "kb-canvas-${hashCode()}"

    // ---- Derived -------------------------------------------------------

    private val importedLibraryNames: Set<String>
        get() = program.statements.filterIsInstance<KBImportStmt>().map { it.libraryName }.toSet()

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

    private fun onMouseMoved(x: Double, y: Double) {
        val current = dragState
        if (current is DragState.DraggingFromPalette) {
            dragState = current.copy(ghostX = x, ghostY = y)
        }
    }

    private fun onMouseReleased(x: Double, y: Double) {
        val current = dragState
        if (current is DragState.DraggingFromPalette) {
            if (isInsideCanvas(x, y)) {
                commitDrop(current.funcName)
            }
            dragState = DragState.None
        }
    }

    private fun isInsideCanvas(x: Double, y: Double): Boolean {
        val el = dom?.querySelector("#$canvasDivId") as? HTMLElement ?: return false
        val rect = el.getBoundingClientRect()
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }

    private fun commitDrop(funcName: String) {
        val chain = KBChainStmt(
            id = uuid(),
            steps = listOf(
                KBCallBlock(id = uuid(), funcName = funcName, isHead = true)
            )
        )
        program = program.copy(statements = program.statements + chain)
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
                onArgChanged = ::onArgChanged,
            )

            // Drag ghost — follows cursor while dragging
            val ds = dragState
            if (ds is DragState.DraggingFromPalette) {
                div {
                    css {
                        position = Position.fixed
                        left = ds.ghostX.px
                        top = ds.ghostY.px
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
                        +ds.funcName
                    }
                }
            }
        }
    }
}

