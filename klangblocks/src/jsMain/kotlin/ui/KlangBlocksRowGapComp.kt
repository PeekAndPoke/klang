package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.klang.blocks.model.DropDestination
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
        val canDrop = dndState?.accepts(DropTarget.RowGap) == true

        // Fixed-height outer container — never changes size
        div {
            css {
                height = 10.px
                position = Position.relative
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false; isDropHovered = false }

            // "+" icon in the line-number gutter — visible on hover when not dragging
            if (isHovered && !canDrop) {
                span {
                    css {
                        position = Position.absolute
                        top = (-13).px
                        left = 4.px
                        width = 28.px
                        height = 28.px
                        display = Display.flex
                        alignItems = Align.center
                        cursor = Cursor.pointer
                        color = Color("#888")
                    }
                    onClick { props.ctx.editing.insertBlankLine(props.index) }
                    onMouseDown { event -> event.preventDefault() }
                    icon.bordered.plus {
                        css {
                            color = Color("#888")
                        }
                    }
                }
            }

            // Drop zone — absolutely positioned so it overlaps rows above and below
            if (canDrop) {
                div {
                    css {
                        position = Position.absolute
                        top = (-10).px
                        bottom = (-10).px
                        left = 28.px
                        right = 0.px
                        display = Display.flex
                        alignItems = Align.center
                        cursor = Cursor.copy
                        put("z-index", "10")
                    }
                    onMouseEnter { isDropHovered = true }
                    onMouseLeave { isDropHovered = false }
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState.onDrop(DropDestination.RowGap(props.index))
                    }
                    // Visual indicator — thin line centred in the hit area
                    div {
                        css {
                            width = 100.pct
                            height = 3.px
                            borderRadius = 2.px
                            backgroundColor = Color(if (isDropHovered) "#6a9fd8" else "rgba(106,159,216,0.35)")
                            put("transition", "background-color 0.1s ease")
                        }
                    }
                }
            }
        }
    }
}
