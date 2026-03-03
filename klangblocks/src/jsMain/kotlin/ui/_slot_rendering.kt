package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.html.*
import io.peekandpoke.klang.blocks.model.DropDestination
import io.peekandpoke.klang.blocks.model.KBChainStmt
import io.peekandpoke.klang.blocks.model.KBIdentifierItem
import io.peekandpoke.klang.blocks.model.KBStringLiteralItem
import kotlinx.css.*
import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span
import org.w3c.dom.Element

/**
 * Renders a nested chain slot container, including the chain segments inside it.
 * Used by both [KlangBlocksBlockComp] and [KlangBlocksLetStmtComp].
 */
internal fun DIV.renderNestedChainSlot(
    chain: KBChainStmt,
    canDrop: Boolean,
    blockId: String,
    slotIndex: Int,
    ctx: KlangBlocksCtx,
) {
    val dndState = ctx.dnd.state
    val headContent: (DIV.() -> Unit)? = when (val h = chain.steps.firstOrNull()) {
        is KBStringLiteralItem -> {
            val item: KBStringLiteralItem = h
            { KlangBlocksStringLiteralItemComp(item = item, chainId = chain.id, ctx = ctx) }
        }

        is KBIdentifierItem -> {
            val name = h.name
            {
                span {
                    css {
                        borderRadius = 3.px
                        padding = Padding(horizontal = 4.px, vertical = 1.px)
                        fontSize = 11.px
                        backgroundColor = Color("rgba(0,0,0,0.25)")
                        border = Border(1.px, BorderStyle.solid, Color("rgba(255,255,255,0.2)"))
                        color = Color("rgba(255,255,255,0.85)")
                        fontFamily = "monospace"
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +name
                }
            }
        }

        else -> null
    }
    div {
        css {
            display = Display.inlineFlex
            flexDirection = FlexDirection.column
            gap = 2.px
            borderRadius = 4.px
            backgroundColor = Color("rgba(0,0,0,0.2)")
            padding = Padding(horizontal = 4.px, vertical = 2.px)
            if (canDrop) {
                border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.5)"))
                cursor = Cursor.copy
                hover {
                    border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.7)"))
                    backgroundColor = Color("rgba(0,0,0,0.35)")
                }
            } else {
                border = Border(1.px, BorderStyle.solid, Color.transparent)
            }
        }
        if (canDrop) {
            onMouseUp { event ->
                val isOverBlock = (event.target as? Element)
                    ?.closest(".kb-block, .kb-nested-block") != null
                if (!isOverBlock) {
                    event.stopPropagation()
                    dndState?.onDrop?.invoke(DropDestination.EmptySlot(blockId, slotIndex))
                }
            }
        }
        onMouseDown { event -> event.stopPropagation() }
        renderChainSegments(
            chain = chain,
            segments = chain.steps.toCallSegments(),
            ctx = ctx,
            variant = BlockVariant.Nested,
            headContent = headContent,
        )
    }
}

/**
 * Renders the inline edit `<input>` used when editing a non-string block slot.
 * Used by both [KlangBlocksBlockComp] and [KlangBlocksLetStmtComp].
 */
internal fun DIV.renderBlockEditInput(
    variant: BlockVariant,
    editText: String,
    onInput: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    input {
        value = editText
        autoFocus = true
        onInput { event -> onInput(event.asDynamic().target.value as String) }
        onBlur { onCommit() }
        onKeyDown { event ->
            when (event.key) {
                "Enter" -> onCommit()
                "Escape" -> onCancel()
            }
        }
        onMouseDown { event -> event.stopPropagation() }
        css {
            backgroundColor = Color("rgba(0,0,0,0.4)")
            border = Border(1.px, BorderStyle.solid, Color("rgba(255,255,255,0.4)"))
            borderRadius = 3.px
            color = Color.white
            fontSize = variant.editFontSize
            fontFamily = "monospace"
            padding = Padding(horizontal = variant.textareaPadH, vertical = 1.px)
            minWidth = variant.textareaMinW
            outline = Outline.none
            put("box-sizing", "border-box")
            put("field-sizing", "content")
        }
    }
}
