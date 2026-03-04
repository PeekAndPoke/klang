package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.klang.blocks.model.DropDestination
import io.peekandpoke.klang.blocks.model.KBNewlineHint
import kotlinx.css.*
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksDropZoneComp(
    variant: KlangBlocksDropZoneComp.Variant,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksDropZoneComp.Props(variant = variant, ctx = ctx)
) {
    KlangBlocksDropZoneComp(it)
}

class KlangBlocksDropZoneComp(ctx: Ctx<Props>) : Component<KlangBlocksDropZoneComp.Props>(ctx) {

    // ── Public variant hierarchy ──────────────────────────────────────────────

    sealed class Variant {
        abstract val chainId: String

        /** Before the first block of a chain with no preceding visible element.
         *  Hidden at rest; expands smoothly when a drag starts. */
        data class InsertBeforeFirst(
            override val chainId: String,
            val blockId: String,
        ) : Variant()

        /** Between blocks, or after a headContent identifier/literal.
         *  Shows a dot-line-dot connector at rest. */
        data class InsertBefore(
            override val chainId: String,
            val blockId: String,
            /** True when a [KBNewlineHint] precedes this block — changes connector to dashed-dot. */
            val hasNewlineBefore: Boolean = false,
            /** When provided, a pill button is shown on hover to toggle the newline before this block. */
            val onToggleNewline: (() -> Unit)? = null,
        ) : Variant()

        /** Segment-end, before a [KBNewlineHint].
         *  Zero-width at rest; drop indicator floats right via overflow:visible. */
        data class InsertAfterSegment(
            override val chainId: String,
            val blockId: String,
        ) : Variant()

        /** End of chain — rendered as a small append circle with a left margin. */
        data class Append(
            override val chainId: String,
        ) : Variant()
    }

    // ── Props ─────────────────────────────────────────────────────────────────

    data class Props(
        val variant: Variant,
        /** Shared context — provides DnD state and editing callbacks. */
        val ctx: KlangBlocksCtx,
    )

    // ── Private idle-mode enum (internal implementation detail) ───────────────

    private enum class IdleMode { Connector, Hidden, FloatIndicator }

    // ── Derived state ─────────────────────────────────────────────────────────

    private var isHovered: Boolean by value(false)

    private val dndState get() = props.ctx.dnd.state

    private val idleMode
        get() = when (props.variant) {
            is Variant.InsertBeforeFirst -> IdleMode.Hidden
            is Variant.InsertBefore -> IdleMode.Connector
            is Variant.InsertAfterSegment -> IdleMode.FloatIndicator
            is Variant.Append -> IdleMode.Connector // unused — append has its own render path
        }

    private val hasNewlineBefore get() = (props.variant as? Variant.InsertBefore)?.hasNewlineBefore ?: false
    private val onToggleNewline get() = (props.variant as? Variant.InsertBefore)?.onToggleNewline

    private val isInline get() = props.variant !is Variant.Append

    private val canDrop
        get() = when (props.variant) {
            is Variant.Append -> dndState?.accepts(DropTarget.ChainEnd) == true
            else -> dndState?.accepts(DropTarget.ChainInsert) == true
        }

    private val domKey
        get() = when (val v = props.variant) {
            is Variant.InsertBeforeFirst -> "${v.chainId}-before-first-${v.blockId}"
            is Variant.InsertBefore -> "${v.chainId}-before-${v.blockId}"
            is Variant.InsertAfterSegment -> "${v.chainId}-after-segment-${v.blockId}"
            is Variant.Append -> "${v.chainId}-append"
        }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (isInline) {
            renderInlineConnector()
        } else {
            renderAppendConnector()
        }
    }

    private fun VDom.renderInlineConnector() {
        val indent = if (hasNewlineBefore) 16 else 0
        val expandedW = (dndState?.ghostWidth ?: 80.0) + 20.0
        val containerW = computeContainerWidth(expandedW, indent)

        div {
            key = domKey
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                flexShrink = 0.0

                when (idleMode) {
                    IdleMode.FloatIndicator -> {
                        marginLeft = 0.px
                        marginRight = 0.px
                        overflow = Overflow.visible
                    }
                    // Negative margins cancel the parent's gap:8px so the zero-width zone
                    // occupies no space at rest, then transitions smoothly when canDrop flips.
                    IdleMode.Hidden -> {
                        overflow = Overflow.hidden
                        if (canDrop) {
                            marginLeft = (-10 + indent).px; marginRight = (-10).px
                        } else {
                            marginLeft = 0.px; marginRight = (-8).px
                        }
                    }

                    IdleMode.Connector -> {
                        marginLeft = (-10 + indent).px
                        marginRight = (-10).px
                    }
                }

                width = containerW.px
                alignSelf = Align.stretch
                position = Position.relative
                zIndex = 1
                cursor = if (canDrop) Cursor.copy else Cursor.default
                put("transition", "width 0.5s ease, margin-left 0.5s ease, margin-right 0.5s ease")
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }

            bindDropHandlers()
            renderHoverActions()
            div {
                key = "drop-indicator"
                css {
                    position = Position.absolute
                    if (idleMode == IdleMode.FloatIndicator) {
                        left = 0.px; top = 50.pct; put("transform", "translateY(-50%)")
                    } else {
                        left = 50.pct; top = 50.pct; put("transform", "translate(-50%, -50%)")
                    }
                    pointerEvents = PointerEvents.none
                }
                renderDropIndicator(expandedW)
            }
            renderIdleConnector()
        }
    }

    private fun computeContainerWidth(expandedW: Double, indent: Int): Double {
        val widthExt = if (isHovered) 32 else 0
        val connectorW = widthExt + if (hasNewlineBefore) 16 else 32
        return when {
            idleMode == IdleMode.FloatIndicator && canDrop && isHovered -> expandedW + 20.0
            idleMode == IdleMode.FloatIndicator -> 0.0
            idleMode == IdleMode.Hidden && !canDrop -> 0.0
            canDrop && isHovered -> expandedW + 20 - indent
            else -> (connectorW + indent).toDouble()
        }
    }

    private fun DIV.bindDropHandlers() {
        if (!canDrop) return
        onMouseOver { it.stopPropagation() }
        onMouseUp { event ->
            event.stopPropagation()
            val destination = when (val v = props.variant) {
                is Variant.InsertBeforeFirst -> DropDestination.ChainInsert(v.chainId, v.blockId)
                is Variant.InsertBefore -> DropDestination.ChainInsert(v.chainId, v.blockId)
                is Variant.InsertAfterSegment -> DropDestination.ChainInsertAfterBlock(v.chainId, v.blockId)
                is Variant.Append -> DropDestination.ChainEnd(v.chainId)
            }
            dndState!!.onDrop(destination)
        }
    }

    private fun DIV.renderHoverActions() {
        if (canDrop || onToggleNewline == null || !isHovered) return
        div(classes = props.ctx.theme.styles.hoverActionsOverlay()) {
            key = "hover-actions"
            renderToggleNewlinePill()
        }
    }

    // Always kept in the DOM so the opacity transition fires smoothly when canDrop becomes
    // true — without this the element is created fresh and the browser paints it instantly.
    private fun DIV.renderDropIndicator(expandedW: Double) {
        div {
            css {
                width = (if (canDrop && isHovered) expandedW else 24.0).px
                height = 24.px
                borderRadius = (if (canDrop && isHovered) 8 else 12).px
                backgroundColor =
                    Color(if (isHovered) props.ctx.theme.dropZoneBackgroundHover else props.ctx.theme.dropZoneBackground).withAlpha(0.9)
                border = Border(1.px, BorderStyle.solid, Color(props.ctx.theme.dropZoneBorder))
                display = Display.flex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                opacity = if (canDrop) 1.0 else 0.0
                pointerEvents = if (canDrop) PointerEvents.auto else PointerEvents.none
                put(
                    "transition",
                    "opacity 0.12s ease, width 0.5s ease, border-radius 0.15s ease, background-color 0.1s ease, border-color 0.1s ease"
                )
            }
            icon.tiny.with(props.ctx.theme.styles.dropZoneIconCls()).plus()
        }
    }

    private fun DIV.renderIdleConnector() {
        if (canDrop || idleMode != IdleMode.Connector) return
        val styles = props.ctx.theme.styles
        if (hasNewlineBefore) {
            // Leading continuation connector: - - - *
            connectorDashedLine(styles)
            connectorDot(styles)
        } else {
            // Standard inter-block connector: * - - - *
            connectorDot(styles)
            connectorSolidLine(styles)
            connectorDot(styles)
        }
    }

    private fun VDom.renderAppendConnector() {
        val expandedW = (dndState?.ghostWidth ?: 80.0) + 20.0
        div(classes = props.ctx.theme.styles.appendConnectorContainer()) {
            key = domKey
            css { cursor = if (canDrop) Cursor.copy else Cursor.default }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            if (canDrop) {
                onMouseOver { it.stopPropagation() }
                onMouseUp { event ->
                    event.stopPropagation()
                    dndState!!.onDrop(DropDestination.ChainEnd(props.variant.chainId))
                }
            }
            div(classes = props.ctx.theme.styles.appendDropIndicatorPos()) {
                key = "drop-indicator"
                renderDropIndicator(expandedW)
            }
        }
    }

    private fun DIV.renderToggleNewlinePill() {
        span(classes = props.ctx.theme.styles.newlineAction()) {
            key = "toggle-newline"
            onClick { event ->
                event.stopPropagation()
                event.preventDefault()
                console.log("toggle new line")
                isHovered = false
                onToggleNewline?.invoke()
            }
            if (hasNewlineBefore) {
                icon.small.level_up_alternate()
            } else {
                icon.small.level_down_alternate()
            }
        }
    }
}
