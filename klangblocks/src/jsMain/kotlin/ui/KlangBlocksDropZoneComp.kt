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

/**
 * Unified chain drop zone.
 *
 * - [insertBeforeBlockId] != null → insert-before mode: fires [DropDestination.ChainInsert]
 *   via [DndState.onDrop]; rendered with negative margins and a dot-line-dot idle connector.
 *
 * - [insertBeforeBlockId] == null → append mode: fires [DropDestination.ChainEnd]
 *   via [DndState.onDrop]; rendered as a small circle with a left margin.
 */
@Suppress("FunctionName")
fun Tag.KlangBlocksDropZoneComp(
    chainId: String,
    insertBeforeBlockId: String?,   // null = append to chain end
    ctx: KlangBlocksCtx,
    showConnectorWhenIdle: Boolean = true,
    hasNewlineBefore: Boolean = false,
    onToggleNewline: (() -> Unit)? = null,
) = comp(
    KlangBlocksDropZoneComp.Props(
        chainId = chainId,
        insertBeforeBlockId = insertBeforeBlockId,
        ctx = ctx,
        showConnectorWhenIdle = showConnectorWhenIdle,
        hasNewlineBefore = hasNewlineBefore,
        onToggleNewline = onToggleNewline,
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
        val hasNewlineBefore: Boolean = false,
        val onToggleNewline: (() -> Unit)? = null,
    )

    private var isHovered: Boolean by value(false)

    override fun VDom.render() {
        val dndState = props.ctx.dnd.state
        val isInline = props.insertBeforeBlockId != null
        val isSourceChain = dndState?.sourceChainId == props.chainId
        val canDrop = !isSourceChain &&
                if (isInline) dndState?.accepts(DropTarget.ChainInsert) == true
                else dndState?.accepts(DropTarget.ChainEnd) == true

        if (isInline) {
            // ── Insert-before mode ───────────────────────────────────────────────
            // Negative margins let it overlap the adjacent blocks' padding so the
            // chain width never changes. Half the width is absorbed on each side.
            val connectorW = if (props.hasNewlineBefore) 16 else 32
            val indent = if (props.hasNewlineBefore) 16 else 0
            div {
                css {
                    display = Display.inlineFlex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    flexShrink = 0.0
                    marginLeft = (-10 + indent).px
                    marginRight = (-10).px
                    width = (connectorW + indent).px
                    alignSelf = Align.stretch
                    position = Position.relative
                    zIndex = 1
                    cursor = when {
                        canDrop -> Cursor.copy
                        props.onToggleNewline != null -> Cursor.pointer
                        else -> Cursor.default
                    }
                }
                onMouseEnter { isHovered = true }
                onMouseLeave { isHovered = false }

                if (!canDrop && props.onToggleNewline != null) {
                    val toggle = props.onToggleNewline!!
                    onClick { event ->
                        event.stopPropagation()
                        event.preventDefault()
                        console.log("toggle new line")
                        toggle()
                    }
                }

                if (canDrop) {
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState!!.onDrop(DropDestination.ChainInsert(props.chainId, props.insertBeforeBlockId!!))
                    }
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
                            if (isHovered) put("transition", "background-color 0.1s ease, border-color 0.1s ease")
                        }
                        icon.tiny.plus {
                            css {
                                color = Color(if (isHovered) "rgba(255,255,255,0.95)" else "rgba(255,255,255,0.2)")
                                margin = Margin(0.px)
                                put("transition", "color 0.1s ease")
                            }
                        }
                    }
                } else if (props.showConnectorWhenIdle) {
                    if (props.hasNewlineBefore) {
                        // Leading continuation connector: - - - *
                        connectorDashedLine()
                        connectorDot()
                    } else {
                        // Standard inter-block connector: * - - - *
                        connectorDot()
                        connectorSolidLine()
                        connectorDot()
                    }
                }
            }
        } else {
            // ── Append mode ──────────────────────────────────────────────────────
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
                        // Transition only when already visible (hover changes); no transition on drag-start appearance.
                        if (isHovered) put("transition", "background-color 0.1s ease, border-color 0.1s ease")
                    }
                }
                if (canDrop) {
                    onMouseEnter { isHovered = true }
                    onMouseLeave { isHovered = false }
                    onMouseUp { event ->
                        event.stopPropagation()
                        dndState!!.onDrop(DropDestination.ChainEnd(props.chainId))
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
