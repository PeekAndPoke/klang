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

/**
 * A unified chain connector / inline drop zone, always rendered between two consecutive blocks.
 *
 * When no drag is active it shows the dot-line-dot connector.
 * When a drag with [DndState.onDropToChainAt] is active it becomes a drop target while keeping
 * the exact same layout footprint (same margins, same width) so the chain never jumps.
 */
@Suppress("FunctionName")
fun Tag.KlangBlocksInlineDropZoneComp(
    chainId: String,
    insertBeforeBlockId: String,
    ctx: KlangBlocksCtx,
    showConnectorWhenIdle: Boolean = true,
) = comp(
    KlangBlocksInlineDropZoneComp.Props(
        chainId = chainId,
        insertBeforeBlockId = insertBeforeBlockId,
        ctx = ctx,
        showConnectorWhenIdle = showConnectorWhenIdle,
    )
) {
    KlangBlocksInlineDropZoneComp(it)
}

class KlangBlocksInlineDropZoneComp(ctx: Ctx<Props>) : Component<KlangBlocksInlineDropZoneComp.Props>(ctx) {

    data class Props(
        val chainId: String,
        val insertBeforeBlockId: String,
        val ctx: KlangBlocksCtx,
        val showConnectorWhenIdle: Boolean = true,
    )

    private var isHovered: Boolean by value(false)

    override fun VDom.render() {
        val dndState = props.ctx.dnd.state
        val canDrop = dndState?.onDropToChainAt != null

        // ── Outer container ─────────────────────────────────────────────────
        // Identical layout to chainConnector: negative margins let it overlap
        // the adjacent blocks' padding, so the chain width never changes.
        div {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                flexShrink = 0.0
                marginLeft = (-10).px
                marginRight = (-10).px
                width = 20.px           // same as 6 + 8 + 6 connector content
                alignSelf = Align.stretch
                position = Position.relative  // anchor for the pill overlay + stacking context
                zIndex = 1                    // paint over adjacent block edges so both dots are fully visible
                if (canDrop) cursor = Cursor.copy
            }
            if (canDrop) {
                onMouseEnter { isHovered = true }
                onMouseLeave { isHovered = false }
                onMouseUp { event ->
                    event.stopPropagation()
                    dndState.onDropToChainAt.invoke(props.chainId, props.insertBeforeBlockId)
                }

                // ── Drag-active state ────────────────────────────────────────
                // Pill is absolutely positioned so it can be slightly larger than
                // the 20 px container without affecting the surrounding layout.
                div {
                    css {
                        position = Position.absolute
                        left = 50.pct
                        top = 50.pct
                        put("transform", "translate(-50%, -50%)")
                        width = 24.px
                        height = 24.px
                        borderRadius = 50.pct
                        backgroundColor = Color(if (isHovered) "rgba(255,255,255,0.2)" else "rgba(255,255,255,0.08)")
                        border = Border(
                            1.px, BorderStyle.solid,
                            Color(if (isHovered) "rgba(255,255,255,0.5)" else "rgba(255,255,255,0.2)")
                        )
                        display = Display.flex
                        alignItems = Align.center
                        justifyContent = JustifyContent.center
                        put("transition", "background-color 0.1s ease, border-color 0.1s ease")
                    }
                    icon.tiny.plus {
                        css {
                            color = Color(if (isHovered) "rgba(255,255,255,0.95)" else "rgba(255,255,255,0.4)")
                            margin = Margin(0.px)
                            put("transition", "color 0.1s ease")
                        }
                    }
                }
            } else if (props.showConnectorWhenIdle) {
                // ── Normal connector ─────────────────────────────────────────
                div { css { width = 6.px; height = 6.px; borderRadius = 50.pct; backgroundColor = Color("#888"); flexShrink = 0.0 } }
                div { css { width = 8.px; height = 2.px; backgroundColor = Color("#888"); flexShrink = 0.0 } }
                div { css { width = 6.px; height = 6.px; borderRadius = 50.pct; backgroundColor = Color("#888"); flexShrink = 0.0 } }
            }
        }
    }
}
