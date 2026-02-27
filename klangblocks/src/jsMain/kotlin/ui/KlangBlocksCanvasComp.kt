package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import io.peekandpoke.klang.blocks.model.*
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksCanvasComp(
    program: KBProgram,
    canvasDivId: String,
    onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
) = comp(
    KlangBlocksCanvasComp.Props(
        program = program,
        canvasDivId = canvasDivId,
        onArgChanged = onArgChanged,
    )
) {
    KlangBlocksCanvasComp(it)
}

class KlangBlocksCanvasComp(ctx: Ctx<Props>) : Component<KlangBlocksCanvasComp.Props>(ctx) {

    data class Props(
        val program: KBProgram,
        val canvasDivId: String,
        val onArgChanged: (stmtId: String, blockId: String, slotIndex: Int, arg: KBArgValue) -> Unit,
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
                                stmt.steps.forEach { item ->
                                    when (item) {
                                        is KBCallBlock -> KlangBlocksBlockComp(
                                            block = item,
                                            onArgChanged = { slotIndex, arg ->
                                                props.onArgChanged(stmt.id, item.id, slotIndex, arg)
                                            },
                                        )
                                        is KBNewlineHint -> span {
                                            css {
                                                color = Color("#888")
                                                padding = Padding(horizontal = 4.px)
                                            }
                                            +"↩"
                                        }
                                    }
                                }
                            }

                            is KBImportStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"import * from \"${stmt.libraryName}\""
                                }
                            }

                            is KBLetStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"let ${stmt.name}"
                                }
                            }

                            is KBConstStmt -> {
                                span {
                                    css { stmtPillStyle() }
                                    +"const ${stmt.name}"
                                }
                            }
                        }
                    }
                }
            }
        }
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
