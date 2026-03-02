package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.html.css
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
    if (current.isNotEmpty()) add(current.toList())
}

/**
 * Renders [segments] as a stacked sequence of row divs inside the receiver [DIV].
 *
 * - Each segment → one flex-row div with drop zones and block components.
 * - The append drop zone is placed after the last segment's last block.
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
    segments.forEachIndexed { segIndex, blocks ->
        div {
            css {
                display = if (variant.isTopLevel) Display.flex else Display.inlineFlex
                flexDirection = FlexDirection.row
                alignItems = Align.center
                put("gap", "8px")
            }
            if (segIndex == 0) headContent?.invoke(this)
            blocks.forEachIndexed { blockIndex, block ->
                val isFirstInSeg = blockIndex == 0
                KlangBlocksDropZoneComp(
                    chainId = chain.id,
                    insertBeforeBlockId = block.id,
                    ctx = ctx,
                    showConnectorWhenIdle = !isFirstBlock,
                    hasNewlineBefore = !isFirstBlock && isFirstInSeg,
                    onToggleNewline = if (!isFirstBlock) {
                        { ctx.editing.onToggleNewlineBeforeBlock(chain.id, block.id) }
                    } else null,
                )
                KlangBlocksBlockComp(block = block, chain = chain, ctx = ctx, variant = variant)
                isFirstBlock = false
            }
            if (segIndex == segments.lastIndex) {
                KlangBlocksDropZoneComp(chainId = chain.id, insertBeforeBlockId = null, ctx = ctx)
            }
        }
    }
    // Empty chain: still show the append drop zone
    if (segments.isEmpty()) {
        KlangBlocksDropZoneComp(chainId = chain.id, insertBeforeBlockId = null, ctx = ctx)
    }
}
