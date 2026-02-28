package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseEnter
import de.peekandpoke.ultra.html.onMouseLeave
import de.peekandpoke.ultra.html.onMouseUp
import de.peekandpoke.ultra.semanticui.icon
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

        // Always rendered at fixed size so the chain row height never changes.
        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                flexShrink = 0.0
                width = 24.px
                height = 24.px
                borderRadius = 50.pct
                marginLeft = 6.px
                alignSelf = Align.center
                if (canDrop) {
                    backgroundColor = Color(if (isHovered) "rgba(255,255,255,0.2)" else "rgba(255,255,255,0.08)")
                    border = Border(
                        1.px, BorderStyle.solid,
                        Color(if (isHovered) "rgba(255,255,255,0.5)" else "rgba(255,255,255,0.2)")
                    )
                    cursor = Cursor.copy
                }
                put("transition", "background-color 0.1s ease, border-color 0.1s ease")
            }
            if (canDrop) {
                onMouseEnter { isHovered = true }
                onMouseLeave { isHovered = false }
                onMouseUp { event ->
                    event.stopPropagation()
                    dndState?.onDropToChain?.invoke(props.chainId)
                }
            }
            if (canDrop) {
                icon.tiny.plus {
                    css {
                        color = Color(if (isHovered) "rgba(255,255,255,0.95)" else "rgba(255,255,255,0.4)")
                        margin = Margin(0.px)
                        put("transition", "color 0.1s ease")
                    }
                }
            }
        }
    }
}
