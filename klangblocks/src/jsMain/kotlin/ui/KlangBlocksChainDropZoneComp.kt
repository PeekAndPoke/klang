package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import de.peekandpoke.ultra.html.onMouseUp
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangBlocksChainDropZoneComp(
    chainId: String,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksChainDropZoneComp.Props(
        chainId = chainId,
        ctx = ctx,
    )
) {
    KlangBlocksChainDropZoneComp(it)
}

class KlangBlocksChainDropZoneComp(ctx: Ctx<Props>) : Component<KlangBlocksChainDropZoneComp.Props>(ctx) {

    data class Props(
        val chainId: String,
        val ctx: KlangBlocksCtx,
    )

    private var isHovered: Boolean by value(false)

    override fun VDom.render() {
        val dndState = props.ctx.dnd.state
        val canDrop = dndState?.onDropToChain != null
        if (!canDrop) return

        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                minWidth = 60.px
                height = 30.px
                borderRadius = 6.px
                border = Border(2.px, BorderStyle.dashed, Color(if (isHovered) "#CCC" else "#AAA"))
                color = Color(if (isHovered) "#CCC" else "#AAA")
                backgroundColor = Color(if (isHovered) "#666" else "transparent")
                fontSize = 16.px
                cursor = Cursor.copy
                put("transition", "all 0.15s ease")
                marginLeft = 4.px
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            onMouseUp { event ->
                event.stopPropagation()
                dndState?.onDropToChain?.invoke(props.chainId)
            }
            +"+"
        }
    }
}
