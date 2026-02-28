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
 * Unified chain drop zone.
 *
 * - [insertBeforeBlockId] != null → insert-before mode: uses [DndState.onDropToChainAt],
 *   rendered with negative margins and a dot-line-dot idle connector
 *   (replaces the former KlangBlocksInlineDropZoneComp).
 *
 * - [insertBeforeBlockId] == null → append mode: uses [DndState.onDropToChain],
 *   rendered as a small circle with a left margin
 *   (replaces the former KlangBlocksChainDropZoneComp).
 */
@Suppress("FunctionName")
fun Tag.KlangBlocksDropZoneComp(
    chainId: String,
    insertBeforeBlockId: String?,   // null = append to chain end
    ctx: KlangBlocksCtx,
    showConnectorWhenIdle: Boolean = true,
) = comp(
    KlangBlocksDropZoneComp.Props(
        chainId = chainId,
        insertBeforeBlockId = insertBeforeBlockId,
        ctx = ctx,
        showConnectorWhenIdle = showConnectorWhenIdle,
    )
) {
    KlangBlocksDropZoneComp(it)
}

class KlangBlocksDropZoneComp(ctx: Ctx<Props>) : Component<KlangBlocksDropZoneComp.Props>(ctx) {

    data class Props(
        val chainId: String,
        val insertBeforeBlockId: String?,
        val ctx: KlangBlocksCtx,
        val showConnectorWhenIdle: Boolean = true,
    )

    private var isHovered: Boolean by value(false)

    override fun VDom.render() {
        val dndState = props.ctx.dnd.state
        val isInline = props.insertBeforeBlockId != null
        val canDrop = if (isInline) dndState?.onDropToChainAt != null else dndState?.onDropToChain != null

        if (isInline) {
            // ── Insert-before mode (former KlangBlocksInlineDropZoneComp) ───────
            // Negative margins let it overlap the adjacent blocks' padding so the
            // chain width never changes.
            div {
                css {
                    display = Display.inlineFlex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    flexShrink = 0.0
                    marginLeft = (-10).px
                    marginRight = (-10).px
                    width = 20.px
                    alignSelf = Align.stretch
                    position = Position.relative
                    zIndex = 1
                    if (canDrop) cursor = Cursor.copy
                }
                if (canDrop) {
                    onMouseEnter { isHovered = true }
                    onMouseLeave { isHovered = false }
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState!!.onDropToChainAt!!.invoke(props.chainId, props.insertBeforeBlockId!!)
                    }
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
                    // dot-line-dot connector
                    div { css { width = 6.px; height = 6.px; borderRadius = 50.pct; backgroundColor = Color("#888"); flexShrink = 0.0 } }
                    div { css { width = 8.px; height = 2.px; backgroundColor = Color("#888"); flexShrink = 0.0 } }
                    div { css { width = 6.px; height = 6.px; borderRadius = 50.pct; backgroundColor = Color("#888"); flexShrink = 0.0 } }
                }
            }
        } else {
            // ── Append mode (former KlangBlocksChainDropZoneComp) ───────────────
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
                        dndState!!.onDropToChain!!.invoke(props.chainId)
                    }
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
}
