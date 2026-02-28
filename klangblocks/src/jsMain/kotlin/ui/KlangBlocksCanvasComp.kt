package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.klang.blocks.model.*
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.KlangBlocksCanvasComp(
    ctx: KlangBlocksCtx,
    canvasDivId: String,
) = comp(
    KlangBlocksCanvasComp.Props(
        ctx = ctx,
        canvasDivId = canvasDivId,
    )
) {
    KlangBlocksCanvasComp(it)
}

class KlangBlocksCanvasComp(ctx: Ctx<Props>) : Component<KlangBlocksCanvasComp.Props>(ctx) {

    data class Props(
        val ctx: KlangBlocksCtx,
        val canvasDivId: String,
    )

    override fun VDom.render() {
        val ctx = props.ctx
        val program = ctx.editing.program

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

                    // Gap before each row (and a final gap after the last row below)
                    KlangBlocksRowGapComp(
                        index = rowIndex,
                        ctx = ctx,
                    )

                    div {
                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = Align.center
                            put("gap", "8px")
                        }

                        // Row number — doubles as a drag handle for KBChainStmt rows
                        span {
                            css {
                                color = Color("#666")
                                fontSize = 11.px
                                fontFamily = "monospace"
                                width = 24.px
                                flexShrink = 0.0
                                textAlign = TextAlign.right
                                if (stmt is KBChainStmt) {
                                    cursor = Cursor.grab
                                    hover { color = Color("#aaa") }
                                }
                            }
                            if (stmt is KBChainStmt) {
                                onMouseDown { event ->
                                    event.preventDefault()
                                    ctx.dnd.startCanvasDrag(
                                        stmt.id, stmt,
                                        event.clientX.toDouble(), event.clientY.toDouble(),
                                    )
                                }
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
                                            // Always the same component between consecutive blocks;
                                            // it switches between connector and drop-zone visuals
                                            // without changing its layout footprint.
                                            if (prevWasBlock) {
                                                KlangBlocksInlineDropZoneComp(
                                                    chainId = stmt.id,
                                                    insertBeforeBlockId = item.id,
                                                    ctx = ctx,
                                                )
                                            }
                                            KlangBlocksBlockComp(
                                                block = item,
                                                chain = stmt,
                                                ctx = ctx,
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

                                KlangBlocksChainDropZoneComp(
                                    chainId = stmt.id,
                                    ctx = ctx,
                                )
                            }

                            is KBImportStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"import * from \"${stmt.libraryName}\""
                                }
                                removeStmtButton { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBLetStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"let ${stmt.name}"
                                }
                                removeStmtButton { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBConstStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"const ${stmt.name}"
                                }
                                removeStmtButton { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBBlankLine -> {
                                span {
                                    css {
                                        display = Display.inlineBlock
                                        height = 16.px
                                    }
                                }
                                removeStmtButton { ctx.editing.onRemoveStmt(stmt.id) }
                            }
                        }
                    }
                }

                // Gap after the last row
                KlangBlocksRowGapComp(
                    index = program.statements.size,
                    ctx = ctx,
                )
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
