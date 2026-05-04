package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.KBAssignStmt
import io.peekandpoke.klang.blocks.model.KBBlankLine
import io.peekandpoke.klang.blocks.model.KBChainStmt
import io.peekandpoke.klang.blocks.model.KBConstStmt
import io.peekandpoke.klang.blocks.model.KBExportStmt
import io.peekandpoke.klang.blocks.model.KBExprStmt
import io.peekandpoke.klang.blocks.model.KBImportStmt
import io.peekandpoke.klang.blocks.model.KBLetStmt
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseDown
import kotlinx.css.paddingBottom
import kotlinx.css.paddingTop
import kotlinx.css.px
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span

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

        div(classes = ctx.theme.styles.canvasContainer()) {
            id = props.canvasDivId

            if (program.statements.isEmpty()) {
                div(classes = ctx.theme.styles.canvasEmptyState()) {
                    +"Drop blocks here"
                }
            } else {
                program.statements.forEachIndexed { rowIndex, stmt ->

                    // Gap before each row (and a final gap after the last row below)
                    KlangBlocksRowGapComp(
                        index = rowIndex,
                        ctx = ctx,
                    )

                    div(classes = ctx.theme.styles.canvasRow()) {
                        key = stmt.id

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
                                div(classes = ctx.theme.styles.chainSegmentColumn()) {
                                    // Multi-segment chains: add matching padding
                                    if (segments.size > 1) {
                                        css { paddingTop = 4.px; paddingBottom = 4.px }
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
                                KlangBlocksVariableStmtComp(stmt = stmt, ctx = ctx)
                            }

                            is KBConstStmt -> {
                                KlangBlocksVariableStmtComp(stmt = stmt, ctx = ctx)
                            }

                            is KBExportStmt -> {
                                KlangBlocksVariableStmtComp(stmt = stmt, ctx = ctx)
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
                                span(classes = ctx.theme.styles.blankLine()) {}
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
