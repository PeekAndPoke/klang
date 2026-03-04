package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import io.peekandpoke.klang.blocks.model.KBCallBlock
import io.peekandpoke.klang.blocks.model.KBChainItem
import io.peekandpoke.klang.blocks.model.KBChainStmt
import io.peekandpoke.klang.blocks.model.KBNewlineHint
import kotlinx.css.*
import kotlinx.html.DIV
import kotlinx.html.div

/**
 * Splits chain steps into visual segments at [KBNewlineHint] boundaries.
 * Non-call items (KBStringLiteralItem, KBIdentifierItem) are skipped silently.
 */
internal fun List<KBChainItem>.toCallSegments(): List<List<KBCallBlock>> = buildList {
    var current = mutableListOf<KBCallBlock>()
    for (step in this@toCallSegments) {
        when (step) {
            is KBCallBlock -> current.add(step)
            is KBNewlineHint -> if (current.isNotEmpty()) {
                add(current.toList())
                current = mutableListOf()
            }
            else -> {}
        }
    }
    if (current.isNotEmpty()) {
        add(current.toList())
    }
}

/**
 * Renders [segments] as a stacked sequence of row divs inside the receiver [DIV].
 *
 * - Each segment → one flex-row div with drop zones and block components.
 * - The append drop zone is placed after the last segment's last block.
 * - Non-last segments end with a trailing `*---` indicator.
 * - Non-first segments are indented so the leading drop-zone connector reads as `---*`.
 * - [headContent], if provided, is rendered at the start of the **first** segment row only
 *   (used for string-literal / identifier heads in nested chains).
 * - [variant] controls block sizing and whether rows use `flex` or `inlineFlex`.
 */
internal fun DIV.renderChainSegments(
    chain: KBChainStmt,
    segments: List<List<KBCallBlock>>,
    ctx: KlangBlocksCtx,
    variant: BlockVariant = BlockVariant.TopLevel,
    headContent: (DIV.() -> Unit)? = null,
) {
    var isFirstBlock = true
    val hasHeadContent = headContent != null
    segments.forEachIndexed { segIndex, blocks ->
        div {
            css {
                display = if (variant.isTopLevel) Display.flex else Display.inlineFlex
                flexDirection = FlexDirection.row
                alignItems = Align.center
                gap = 8.px
                if (segIndex > 0) {
                    paddingLeft = 24.px
                }
            }
            if (segIndex == 0) {
                headContent?.invoke(this)
            }
            blocks.forEachIndexed { blockIndex, block ->
                val isFirstInSeg = blockIndex == 0
                val dropZoneVariant = when {
                    isFirstBlock && !hasHeadContent -> KlangBlocksDropZoneComp.Variant.InsertBeforeFirst(chain.id, block.id)
                    else -> KlangBlocksDropZoneComp.Variant.InsertBefore(
                        chainId = chain.id,
                        blockId = block.id,
                        hasNewlineBefore = !isFirstBlock && isFirstInSeg,
                        onToggleNewline = { ctx.editing.onToggleNewlineBeforeBlock(chain.id, block.id) },
                    )
                }
                KlangBlocksDropZoneComp(variant = dropZoneVariant, ctx = ctx)
                KlangBlocksBlockComp(block = block, chain = chain, ctx = ctx, variant = variant)
                isFirstBlock = false
            }
            if (segIndex == segments.lastIndex) {
                KlangBlocksDropZoneComp(variant = KlangBlocksDropZoneComp.Variant.Append(chain.id), ctx = ctx)
            } else {
                val nextSegFirstBlockId = segments[segIndex + 1].first().id
                // Trailing line-break indicator: *---  (click toggles the newline back off)
                div {
                    css {
                        display = Display.inlineFlex
                        alignItems = Align.center
                        width = 36.px
                        flexShrink = 0.0
                        cursor = Cursor.pointer
                        marginLeft = (-10).px
                        position = Position.relative
                        zIndex = 1
                    }
                    onClick { event ->
                        event.stopPropagation()
                        event.preventDefault()
                        ctx.editing.onToggleNewlineBeforeBlock(chain.id, nextSegFirstBlockId)
                    }
                    connectorDot()
                    connectorDashedLine()
                }
                // Segment-end drop zone: floats right of *--- at zero idle width.
                // Fires ChainInsertAfterBlock so the dropped block stays on this row (before the KBNewlineHint).
                KlangBlocksDropZoneComp(
                    variant = KlangBlocksDropZoneComp.Variant.InsertAfterSegment(chain.id, blocks.last().id),
                    ctx = ctx,
                )
            }
        }
    }
    // Empty chain: still show the append drop zone
    if (segments.isEmpty()) {
        KlangBlocksDropZoneComp(variant = KlangBlocksDropZoneComp.Variant.Append(chain.id), ctx = ctx)
    }
}

// ── Connector drawing primitives ─────────────────────────────────────────────

/** A small filled circle used as a chain connector end-point. */
internal fun DIV.connectorDot(color: String = "#888") {
    div {
        css {
            width = 6.px
            height = 6.px
            borderRadius = 50.pct
            backgroundColor = Color(color)
            flexShrink = 0.0
        }
    }
}

/** A solid horizontal line that grows to fill available width. */
internal fun DIV.connectorSolidLine(color: String = "#888") {
    div {
        css {
            flexGrow = 1.0
            height = 2.px
            backgroundColor = Color(color)
            flexShrink = 0.0
        }
    }
}

/** A dashed horizontal line that grows to fill available width. */
internal fun DIV.connectorDashedLine(color: String = "#888") {
    div {
        css {
            flexGrow = 1.0
            height = 0.px
            borderTop = Border(2.px, BorderStyle.dotted, Color(color))
        }
    }
}
