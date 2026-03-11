package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.klang.blocks.model.DropDestination
import kotlinx.css.Color
import kotlinx.css.backgroundColor
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
        val styles = props.ctx.theme.styles

        // Fixed-height outer container — never changes size
        div(classes = styles.rowGapContainer()) {
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false; isDropHovered = false }

            // "+" icon in the line-number gutter — visible on hover when not dragging
            if (isHovered && !canDrop) {
                span(classes = styles.rowInsertIcon()) {
                    onClick { props.ctx.editing.insertBlankLine(props.index) }
                    onMouseDown { event -> event.preventDefault() }
                    icon.bordered.plus()
                }
            }

            // Drop zone — absolutely positioned so it overlaps rows above and below
            if (canDrop) {
                div(classes = styles.rowGapDropZone()) {
                    onMouseEnter { isDropHovered = true }
                    onMouseLeave { isDropHovered = false }
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState.onDrop(DropDestination.RowGap(props.index))
                    }
                    // Visual indicator — thin line centred in the hit area
                    div(classes = styles.rowDropLine()) {
                        css {
                            backgroundColor = Color(
                                if (isDropHovered) {
                                    props.ctx.theme.rowDropLineHover
                                } else {
                                    props.ctx.theme.rowDropLineIdle
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
