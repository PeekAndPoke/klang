package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.klang.blocks.model.*
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.KlangBlocksCanvasComp(
    program: KBProgram,
    canvasDivId: String,
    isDragging: Boolean,
    onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
    onRemoveBlock: (stmtId: String, blockId: String) -> Unit,
    onRemoveStmt: (stmtId: String) -> Unit,
    onChainHoverStart: (chainId: String) -> Unit,
    onChainHoverEnd: () -> Unit,
) = comp(
    KlangBlocksCanvasComp.Props(
        program = program,
        canvasDivId = canvasDivId,
        isDragging = isDragging,
        onArgChanged = onArgChanged,
        onRemoveBlock = onRemoveBlock,
        onRemoveStmt = onRemoveStmt,
        onChainHoverStart = onChainHoverStart,
        onChainHoverEnd = onChainHoverEnd,
    )
) {
    KlangBlocksCanvasComp(it)
}

class KlangBlocksCanvasComp(ctx: Ctx<Props>) : Component<KlangBlocksCanvasComp.Props>(ctx) {

    data class Props(
        val program: KBProgram,
        val canvasDivId: String,
        val isDragging: Boolean,
        val onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
        val onRemoveBlock: (stmtId: String, blockId: String) -> Unit,
        val onRemoveStmt: (stmtId: String) -> Unit,
        val onChainHoverStart: (chainId: String) -> Unit,
        val onChainHoverEnd: () -> Unit,
    )

    override fun VDom.render() {
        val program = props.program

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
                program.statements.forEachIndexed { rowIndex, stmt ->
                    div {
                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = Align.center
                            put("gap", "8px")
                            marginBottom = 8.px
                        }

                        // Row number
                        span {
                            css {
                                color = Color("#666")
                                fontSize = 11.px
                                fontFamily = "monospace"
                                width = 24.px
                                flexShrink = 0.0
                                textAlign = TextAlign.right
                            }
                            val n = rowIndex + 1
                            +(if (n < 10) "0$n" else "$n")
                        }

                        when (stmt) {
                            is KBChainStmt -> {
                                var prevWasBlock = false
                                stmt.steps.forEach { item ->
                                    when (item) {
                                        is KBCallBlock -> {
                                            if (prevWasBlock) chainConnector()
                                            KlangBlocksBlockComp(
                                                block = item,
                                                onArgChanged = { slotIndex, arg ->
                                                    props.onArgChanged(stmt.id, item.id, slotIndex, arg)
                                                },
                                                onRemove = {
                                                    props.onRemoveBlock(stmt.id, item.id)
                                                },
                                            )
                                            prevWasBlock = true
                                        }

                                        is KBNewlineHint -> {
                                            span {
                                                css {
                                                    color = Color("#888")
                                                    padding = Padding(horizontal = 4.px)
                                                }
                                                +"↩"
                                            }
                                            prevWasBlock = false
                                        }
                                    }
                                }

                                // Chain drop zone — visible while dragging
                                if (props.isDragging) {
                                    chainDropZone(stmt.id)
                                }
                            }

                            is KBImportStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"import * from \"${stmt.libraryName}\""
                                }
                                removeStmtButton { props.onRemoveStmt(stmt.id) }
                            }

                            is KBLetStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"let ${stmt.name}"
                                }
                                removeStmtButton { props.onRemoveStmt(stmt.id) }
                            }

                            is KBConstStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"const ${stmt.name}"
                                }
                                removeStmtButton { props.onRemoveStmt(stmt.id) }
                            }
                        }
                    }
                }
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
                // Cancel the parent row's gap on both sides so each dot lands
                // flush against the adjacent block edge.
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

    /** A "chain here" drop target shown at the end of each chain row while dragging. */
    private fun DIV.chainDropZone(chainId: String) {
        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                minWidth = 32.px
                height = 30.px
                borderRadius = 6.px
                border = Border(1.px, BorderStyle.dashed, Color("#888"))
                color = Color("#888")
                fontSize = 16.px
                cursor = Cursor.copy
                put("transition", "all 0.15s ease")
                marginLeft = 4.px
                hover {
                    backgroundColor = Color("rgba(255,255,255,0.08)")
                    borderColor = Color("#aaa")
                    color = Color("#ddd")
                }
            }
            onMouseEnter { props.onChainHoverStart(chainId) }
            onMouseLeave { props.onChainHoverEnd() }
            +"+"
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
