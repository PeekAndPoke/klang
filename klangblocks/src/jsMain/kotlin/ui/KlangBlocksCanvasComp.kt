package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
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
                overflowX = Overflow.auto
                padding = Padding(16.px)
                backgroundColor = Color(ctx.theme.canvasBackground)
                minHeight = 400.px
            }

            if (program.statements.isEmpty()) {
                div {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        justifyContent = JustifyContent.center
                        height = 200.px
                        color = Color(ctx.theme.textDisabled)
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
                        key = stmt.id

                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = Align.flexStart
                            gap = 8.px
                        }

                        // Row number — drag handle for rows that support reordering
                        val isDraggableRow = stmt is KBChainStmt || stmt is KBLetStmt || stmt is KBConstStmt
                        val rowNumClasses = buildString {
                            append(ctx.theme.styles.rowNumber())
                            if (isDraggableRow) append(" ${ctx.theme.styles.rowNumberDraggable()}")
                        }
                        span(classes = rowNumClasses) {
                            if (isDraggableRow) {
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
                                val segments = stmt.steps.toCallSegments()
                                div {
                                    css {
                                        display = Display.flex
                                        flexDirection = FlexDirection.column
                                        gap = 4.px
                                        // Multi-segment chains: add matching padding so the top of the
                                        // first row and bottom of the last row have the same breathing
                                        // room as the internal gap between segments.
                                        if (segments.size > 1) {
                                            paddingTop = 4.px
                                            paddingBottom = 4.px
                                        }
                                    }
                                    renderChainSegments(
                                        chain = stmt,
                                        segments = segments,
                                        ctx = ctx,
                                    )
                                }
                            }

                            is KBImportStmt -> {
                                span(classes = ctx.theme.styles.stmtPill()) {
                                    +"import * from \"${stmt.libraryName}\""
                                }
                                removeStmtButton(ctx.theme) { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBLetStmt -> {
                                KlangBlocksLetStmtComp(stmt = stmt, ctx = ctx)
                            }

                            is KBConstStmt -> {
                                KlangBlocksLetStmtComp(stmt = stmt, ctx = ctx)
                            }

                            is KBAssignStmt -> {
                                span(classes = ctx.theme.styles.stmtPill()) {
                                    +"${stmt.target} = …"
                                }
                                removeStmtButton(ctx.theme) { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBExprStmt -> {
                                span(classes = ctx.theme.styles.stmtPill()) {
                                    +stmt.expr.renderShort()
                                }
                                removeStmtButton(ctx.theme) { ctx.editing.onRemoveStmt(stmt.id) }
                            }

                            is KBBlankLine -> {
                                span {
                                    css {
                                        display = Display.inlineBlock
                                        height = 16.px
                                    }
                                }
                                removeStmtButton(ctx.theme) { ctx.editing.onRemoveStmt(stmt.id) }
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
private fun DIV.removeStmtButton(theme: KlangBlocksTheme, onRemove: () -> Unit) {
    span(classes = theme.styles.stmtPillRemoveBtn()) {
        onClick { onRemove() }
        +"×"
    }
}
