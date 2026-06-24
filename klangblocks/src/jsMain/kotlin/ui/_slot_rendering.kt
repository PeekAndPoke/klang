/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.DropDestination
import io.peekandpoke.klang.blocks.model.KBChainStmt
import io.peekandpoke.klang.blocks.model.KBIdentifierItem
import io.peekandpoke.klang.blocks.model.KBStringLiteralItem
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onBlur
import io.peekandpoke.ultra.html.onInput
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.ultra.html.onMouseUp
import kotlinx.css.Border
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.Outline
import kotlinx.css.Padding
import kotlinx.css.backgroundColor
import kotlinx.css.border
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.gap
import kotlinx.css.minWidth
import kotlinx.css.outline
import kotlinx.css.padding
import kotlinx.css.px
import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span
import org.w3c.dom.Element

/**
 * Renders a nested chain slot container, including the chain segments inside it.
 * Used by both [KlangBlocksBlockComp] and [KlangBlocksVariableStmtComp].
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
            {
                KlangBlocksStringEditorComp(
                    value = item.value,
                    ctx = ctx,
                    onCommit = {
                        ctx.editing.onStringLiteralItemChanged(chain.id, it)
                    },
                    blockId = chain.id,
                    slotIndex = 0,
                )
            }
        }

        is KBIdentifierItem -> {
            val name = h.name
            {
                span(classes = ctx.theme.styles.inlineItem()) {
                    +name
                }
            }
        }

        else -> null
    }
    div(classes = if (canDrop) ctx.theme.styles.nestedSlotDrop() else "") {
        css {
            display = Display.inlineFlex
            flexDirection = FlexDirection.column
            gap = 2.px
            borderRadius = 4.px
            backgroundColor = Color(ctx.theme.slotBackground)
            padding = Padding(horizontal = 4.px, vertical = 2.px)
            if (!canDrop) {
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
            isTopLevel = false,
            headContent = headContent,
        )
    }
}

/**
 * Renders the inline edit `<input>` used when editing a non-string block slot.
 * Used by both [KlangBlocksBlockComp] and [KlangBlocksVariableStmtComp].
 */
internal fun DIV.renderBlockEditInput(
    theme: KlangBlocksTheme,
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
            backgroundColor = Color(theme.inputBackground)
            border = Border(1.px, BorderStyle.solid, Color(theme.inputBorder))
            borderRadius = 3.px
            color = Color(theme.textPrimary)
            fontSize = 12.px
            fontFamily = "monospace"
            padding = Padding(horizontal = 4.px, vertical = 1.px)
            minWidth = 100.px
            outline = Outline.none
            put("box-sizing", "border-box")
            put("field-sizing", "content")
        }
    }
}
