package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.mutator.Mutator
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import io.peekandpoke.klang.blocks.model.KBArgValue
import io.peekandpoke.klang.blocks.model.KBChainStmt
import io.peekandpoke.klang.blocks.model.KBProgram
import io.peekandpoke.klang.blocks.model.statements
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.KlangBlocksCanvasComp(
    program: Mutator<KBProgram>,
    canvasDivId: String,
    dndState: DndState?,
    onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
    onRemoveBlock: (stmtId: String, blockId: String) -> Unit,
    onRemoveStmt: (stmtId: String) -> Unit,
    onCanvasDragStart: (stmtId: String, chain: KBChainStmt, x: Double, y: Double) -> Unit,
    onInsertBlankLine: (index: Int) -> Unit,
) = comp(
    KlangBlocksCanvasComp.Props(
        program = program,
        canvasDivId = canvasDivId,
        dndState = dndState,
        onArgChanged = onArgChanged,
        onRemoveBlock = onRemoveBlock,
        onRemoveStmt = onRemoveStmt,
        onCanvasDragStart = onCanvasDragStart,
        onInsertBlankLine = onInsertBlankLine,
    )
) {
    KlangBlocksCanvasComp(it)
}

class KlangBlocksCanvasComp(ctx: Ctx<Props>) : Component<KlangBlocksCanvasComp.Props>(ctx) {

    data class Props(
        val program: Mutator<KBProgram>,
        val canvasDivId: String,
        val dndState: DndState?,
        val onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
        val onRemoveBlock: (stmtId: String, blockId: String) -> Unit,
        val onRemoveStmt: (stmtId: String) -> Unit,
        val onCanvasDragStart: (stmtId: String, chain: KBChainStmt, x: Double, y: Double) -> Unit,
        val onInsertBlankLine: (index: Int) -> Unit,
    )

    override fun VDom.render() {
        val program = props.program
        val dndState = props.dndState

        div {
            id = props.canvasDivId
            css {
                flex = Flex(1.0, 1.0, FlexBasis.auto)
                overflowY = Overflow.auto
                padding = Padding(16.px)
                backgroundColor = Color("#1e1e2e")
                minHeight = 400.px
            }

            if (program.statements.isEmpty()) {
                div {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        justifyContent = JustifyContent.center
                        height = 200.px
                        color = Color("#555")
                        fontSize = 16.px
                    }
                    +"Drop blocks here"
                }
            } else {
                try {
                    console.log("program.statements:", program.statements)
                    val stmts = program.statements

                    stmts.forEach { stmt ->
                        console.log("stmt:", stmt)
                    }
                } catch (e: Throwable) {
                    console.error("Error accessing program.statements:", e, e.stackTraceToString())
                }


//                program.statements.forEachIndexed { rowIndex, stmtMutator ->
//
//                    return@forEachIndexed
//
//                    val stmt = stmtMutator()
//
//                    // Gap before each row (and a final gap after the last row below)
//                    KlangBlocksRowGapComp(
//                        index = rowIndex,
//                        dndState = dndState,
//                        onInsertBlankLine = props.onInsertBlankLine,
//                    )
//
//                    div {
//                        css {
//                            display = Display.flex
//                            flexDirection = FlexDirection.row
//                            alignItems = Align.center
//                            put("gap", "8px")
//                            marginBottom = 4.px
//                        }
//
//                        // Row number — doubles as a drag handle for KBChainStmt rows
//                        span {
//                            css {
//                                color = Color("#666")
//                                fontSize = 11.px
//                                fontFamily = "monospace"
//                                width = 24.px
//                                flexShrink = 0.0
//                                textAlign = TextAlign.right
//                                if (stmt is KBChainStmt) {
//                                    cursor = Cursor.grab
//                                    hover { color = Color("#aaa") }
//                                }
//                            }
//                            if (stmt is KBChainStmt) {
//                                onMouseDown { event ->
//                                    event.preventDefault()
//                                    props.onCanvasDragStart(
//                                        stmt.id, stmt,
//                                        event.clientX.toDouble(), event.clientY.toDouble(),
//                                    )
//                                }
//                            }
//                            val n = rowIndex + 1
//                            +(if (n < 10) "0$n" else "$n")
//                        }
//
//                        when (stmt) {
//                            is KBChainStmt -> stmtMutator.cast(stmt) {
//                                var prevWasBlock = false
//                                stmt.steps.forEach { item ->
//                                    when (item) {
//                                        is KBCallBlock -> {
//                                            if (prevWasBlock) chainConnector()
//                                            KlangBlocksBlockComp(
//                                                block = item,
//                                                stmtId = stmt.id,
//                                                dndState = dndState,
//                                                onArgChanged = { slotIndex, arg ->
//                                                    props.onArgChanged(stmt.id, item.id, slotIndex, arg)
//                                                },
//                                                onRemove = {
//                                                    props.onRemoveBlock(stmt.id, item.id)
//                                                },
//                                                onDragStart = { x, y ->
//                                                    props.onCanvasDragStart(stmt.id, stmt, x, y)
//                                                },
//                                            )
//                                            prevWasBlock = true
//                                        }
//
//                                        is KBNewlineHint -> {
//                                            span {
//                                                css {
//                                                    color = Color("#888")
//                                                    padding = Padding(horizontal = 4.px)
//                                                }
//                                                +"↩"
//                                            }
//                                            prevWasBlock = false
//                                        }
//                                    }
//                                }
//
//                                KlangBlocksChainDropZoneComp(
//                                    chainId = stmt.id,
//                                    dndState = dndState,
//                                )
//                            }
//
//                            is KBImportStmt -> stmtMutator.cast(stmt) {
//                                span {
//                                    css { stmtPillStyle() }
//                                    +"import * from \"${stmt.libraryName}\""
//                                }
//                                removeStmtButton { props.onRemoveStmt(stmt.id) }
//                            }
//
//                            is KBLetStmt -> stmtMutator.cast(stmt) {
//                                span {
//                                    css { stmtPillStyle() }
//                                    +"let ${stmt.name}"
//                                }
//                                removeStmtButton { props.onRemoveStmt(stmt.id) }
//                            }
//
//                            is KBConstStmt -> stmtMutator.cast(stmt) {
//                                span {
//                                    css { stmtPillStyle() }
//                                    +"const ${stmt.name}"
//                                }
//                                removeStmtButton { props.onRemoveStmt(stmt.id) }
//                            }
//
//                            is KBBlankLine -> stmtMutator.cast(stmt) {
//                                span {
//                                    css {
//                                        display = Display.inlineBlock
//                                        height = 16.px
//                                    }
//                                }
//                                removeStmtButton { props.onRemoveStmt(stmt.id) }
//                            }
//                        }
//                    }
//                }

                // Gap after the last row
                KlangBlocksRowGapComp(
                    index = program.statements.size,
                    dndState = dndState,
                    onInsertBlankLine = props.onInsertBlankLine,
                )
            }
        }
    }

    /** Dot-line-dot connector rendered between consecutive blocks in a chain. */
    private fun DIV.chainConnector() {
        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                flexShrink = 0.0
                marginLeft = (-10).px
                marginRight = (-10).px
            }
            // Left dot
            div {
                css {
                    width = 6.px
                    height = 6.px
                    borderRadius = 50.pct
                    backgroundColor = Color("#888")
                    flexShrink = 0.0
                }
            }
            // Line
            div {
                css {
                    width = 8.px
                    height = 2.px
                    backgroundColor = Color("#888")
                    flexShrink = 0.0
                }
            }
            // Right dot
            div {
                css {
                    width = 6.px
                    height = 6.px
                    borderRadius = 50.pct
                    backgroundColor = Color("#888")
                    flexShrink = 0.0
                }
            }
        }
    }
}

/** Inline × to remove a non-chain statement (import, let, const). */
private fun DIV.removeStmtButton(onRemove: () -> Unit) {
    span {
        css {
            fontSize = 13.px
            color = Color("#555")
            cursor = Cursor.pointer
            borderRadius = 3.px
            padding = Padding(horizontal = 4.px, vertical = 2.px)
            hover {
                backgroundColor = Color("rgba(255,255,255,0.08)")
                color = Color("#ccc")
            }
        }
        onClick { onRemove() }
        +"×"
    }
}

private fun CssBuilder.stmtPillStyle() {
    backgroundColor = Color("#333")
    color = Color("#aaa")
    padding = Padding(vertical = 4.px, horizontal = 8.px)
    borderRadius = 4.px
    fontFamily = "monospace"
    fontSize = 12.px
}
