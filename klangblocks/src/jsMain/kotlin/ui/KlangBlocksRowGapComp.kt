package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksRowGapComp(
    index: Int,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksRowGapComp.Props(
        index = index,
        ctx = ctx,
    )
) {
    KlangBlocksRowGapComp(it)
}

class KlangBlocksRowGapComp(ctx: Ctx<Props>) : Component<KlangBlocksRowGapComp.Props>(ctx) {

    data class Props(
        val index: Int,
        val ctx: KlangBlocksCtx,
    )

    private var isHovered: Boolean by value(false)
    private var isDropHovered: Boolean by value(false)

    override fun VDom.render() {
        val dndState = props.ctx.dnd.state
        val canDrop = dndState?.onDropToPosition != null

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = Align.center
                height = if (isHovered || canDrop) 20.px else 6.px
                put("transition", "height 0.1s ease")
                position = Position.relative
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false; isDropHovered = false }

            // Blank-line button — visible on hover, independent of drag
            if (isHovered && !canDrop) {
                span {
                    css {
                        marginLeft = 28.px  // align with row content (past the row-number column)
                        fontSize = 10.px
                        color = Color("#555")
                        cursor = Cursor.pointer
                        borderRadius = 3.px
                        padding = Padding(horizontal = 4.px, vertical = 1.px)
                        userSelect = UserSelect.none
                        hover {
                            color = Color("#aaa")
                            backgroundColor = Color("rgba(255,255,255,0.06)")
                        }
                    }
                    onClick { props.ctx.editing.insertBlankLine(props.index) }
                    onMouseDown { event -> event.preventDefault() }
                    +"+ blank line"
                }
            }

            // Row drop zone — visible while any draggable thing is active
            if (canDrop) {
                div {
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        marginLeft = 28.px
                        height = 4.px
                        borderRadius = 2.px
                        backgroundColor = Color(if (isDropHovered) "#6a9fd8" else "rgba(106,159,216,0.35)")
                        put("transition", "background-color 0.1s ease")
                        cursor = Cursor.copy
                    }
                    onMouseEnter { isDropHovered = true }
                    onMouseLeave { isDropHovered = false }
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState?.onDropToPosition?.invoke(props.index)
                    }
                }
            }
        }
    }
}
