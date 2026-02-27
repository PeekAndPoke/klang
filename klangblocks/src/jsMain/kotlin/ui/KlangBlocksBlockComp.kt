package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksBlockComp(
    block: KBCallBlock,
    chain: KBChainStmt,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksBlockComp.Props(
        block = block,
        chain = chain,
        ctx = ctx,
    )
) {
    KlangBlocksBlockComp(it)
}

class KlangBlocksBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksBlockComp.Props>(ctx) {

    data class Props(
        val block: KBCallBlock,
        val chain: KBChainStmt,
        val ctx: KlangBlocksCtx,
    )

    private var editingSlotIndex: Int? by value(null)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)

    private fun startEdit(slotIndex: Int, currentText: String) {
        editingSlotIndex = slotIndex
        editText = currentText
    }

    private fun commitEdit(slotIndex: Int) {
        val text = editText.trim()
        val arg: KBArgValue = if (text.isEmpty()) {
            KBEmptyArg("")
        } else {
            val num = text.toDoubleOrNull()
            if (num != null) KBNumberArg(num) else KBStringArg(text)
        }
        props.ctx.editing.onArgChanged(props.block.id, slotIndex, arg)
        editingSlotIndex = null
        editText = ""
    }

    private fun cancelEdit() {
        editingSlotIndex = null
        editText = ""
    }

    override fun VDom.render() {
        val block = props.block
        val ctx = props.ctx
        val dndState = ctx.dnd.state
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()
        val canDropToSlot = dndState?.onDropToSlot != null

        div("kb-block") {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                put("gap", "4px")
                padding = Padding(horizontal = 10.px, vertical = 5.px)
                borderRadius = 8.px
                backgroundColor = Color(categoryColour(doc?.category))
                color = Color.white
                fontSize = 13.px
                fontFamily = "monospace"
                whiteSpace = WhiteSpace.nowrap
                userSelect = UserSelect.none
                position = Position.relative
                if (!canDropToSlot) cursor = Cursor.grab
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            // Start a canvas drag when pressing on the block background or function name.
            // Slot spans stop mousedown propagation, so they won't trigger this.
            onMouseDown { event ->
                if (!canDropToSlot) {
                    event.preventDefault()
                    ctx.dnd.startCanvasDrag(
                        props.chain.id, props.chain,
                        event.clientX.toDouble(), event.clientY.toDouble(),
                    )
                }
            }

            span {
                css { fontWeight = FontWeight.bold }
                +block.funcName
            }

            // For vararg slots: show filled slots + 1 empty, minimum 1.
            // Find the highest slot index that is vararg and has a filled arg.
            val lastFilledVarargIndex = slots.indices
                .filter { slots[it].isVararg }
                .lastOrNull { i -> val a = block.args.getOrNull(i); a != null && a !is KBEmptyArg }
            // Show vararg slots up to this index (inclusive): last filled + 1, or just the first vararg slot.
            val varargShowUpTo = lastFilledVarargIndex?.plus(1) ?: slots.indexOfFirst { it.isVararg }

            slots.forEachIndexed { i, slot ->
                if (slot.isVararg && i > varargShowUpTo) return@forEachIndexed
                val arg: KBArgValue? = block.args.getOrNull(i)
                val canDrop = canDropToSlot && slotAcceptsChainDrop(slot)

                if (editingSlotIndex == i) {
                    input {
                        value = editText
                        autoFocus = true
                        onInput { event ->
                            editText = event.asDynamic().target.value as String
                        }
                        onBlur { commitEdit(i) }
                        onKeyDown { event ->
                            when (event.key) {
                                "Enter" -> commitEdit(i)
                                "Escape" -> cancelEdit()
                            }
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        css {
                            backgroundColor = Color("rgba(0,0,0,0.4)")
                            border = Border(1.px, BorderStyle.solid, Color("rgba(255,255,255,0.4)"))
                            borderRadius = 3.px
                            color = Color.white
                            fontSize = 12.px
                            fontFamily = "monospace"
                            padding = Padding(horizontal = 4.px, vertical = 1.px)
                            minWidth = 50.px
                            maxWidth = 140.px
                            outline = Outline.none
                            put("box-sizing", "border-box")
                        }
                    }
                } else if (arg is KBNestedChainArg && !canDrop) {
                    // Render the nested chain as inline mini-blocks
                    span {
                        css {
                            display = Display.inlineFlex
                            alignItems = Align.center
                            put("gap", "2px")
                            borderRadius = 4.px
                            backgroundColor = Color("rgba(0,0,0,0.2)")
                            padding = Padding(horizontal = 4.px, vertical = 2.px)
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        var prevWasBlock = false
                        arg.chain.steps.forEach { nestedItem ->
                            when (nestedItem) {
                                is KBCallBlock -> {
                                    if (prevWasBlock) {
                                        span {
                                            css {
                                                color = Color("rgba(255,255,255,0.4)")
                                                fontSize = 10.px
                                                padding = Padding(horizontal = 1.px)
                                            }
                                            +"•"
                                        }
                                    }
                                    KlangBlocksNestedBlockComp(
                                        block = nestedItem,
                                        ctx = ctx,
                                    )
                                    prevWasBlock = true
                                }

                                is KBNewlineHint -> prevWasBlock = false
                            }
                        }
                    }
                } else {
                    span {
                        css {
                            borderRadius = 4.px
                            padding = Padding(horizontal = 6.px, vertical = 2.px)
                            fontSize = 12.px
                            if (canDrop) {
                                // Highlight as a valid drop target
                                backgroundColor = Color("rgba(255,255,255,0.25)")
                                border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.7)"))
                                cursor = Cursor.copy
                            } else {
                                backgroundColor = Color("rgba(0,0,0,0.2)")
                                cursor = Cursor.text
                                if (arg == null || arg is KBEmptyArg) opacity = 0.6
                            }
                        }
                        if (canDrop) {
                            onMouseUp { event ->
                                event.stopPropagation()
                                dndState?.onDropToSlot?.invoke(props.chain.id, block.id, i)
                            }
                            onMouseDown { event -> event.stopPropagation() }
                        } else {
                            onClick { event ->
                                event.stopPropagation()
                                val currentText = when (arg) {
                                    is KBStringArg -> arg.value
                                    is KBNumberArg -> {
                                        val l = arg.value.toLong()
                                        if (arg.value == l.toDouble()) l.toString() else arg.value.toString()
                                    }

                                    else -> ""
                                }
                                startEdit(i, currentText)
                            }
                            onMouseDown { event -> event.stopPropagation() }
                        }
                        when (arg) {
                            null, is KBEmptyArg -> +"[${slot.name}]"
                            else -> +arg.renderShort()
                        }
                    }
                }
            }

            // Remove (×) — appears on hover, hidden while a drag is active to avoid clutter
            if (isHovered && !canDropToSlot) {
                span {
                    css {
                        marginLeft = 4.px
                        fontSize = 12.px
                        lineHeight = LineHeight("1")
                        color = Color("rgba(255,255,255,0.55)")
                        cursor = Cursor.pointer
                        borderRadius = 3.px
                        padding = Padding(horizontal = 3.px, vertical = 1.px)
                        hover {
                            backgroundColor = Color("rgba(255,255,255,0.18)")
                            color = Color.white
                        }
                    }
                    onClick { event ->
                        event.stopPropagation()
                        ctx.editing.onRemoveBlock(props.block.id)
                    }
                    onMouseDown { event -> event.stopPropagation() }
                    +"×"
                }
            }
        }
    }
}

/** Returns true if a slot can accept a KBChainStmt dropped from the canvas. */
private fun slotAcceptsChainDrop(slot: KBSlot): Boolean = when (val k = slot.kind) {
    is KBSlotKind.PatternResult -> true
    is KBSlotKind.Union -> k.acceptsBlock
    else -> false
}

internal fun KBArgValue.renderShort(): String = when (this) {
    is KBEmptyArg -> ""
    is KBStringArg -> "\"$value\""
    is KBNumberArg -> value.toString()
    is KBBoolArg -> value.toString()
    is KBIdentifierArg -> name
    is KBNestedChainArg -> chain.steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName }
    is KBBinaryArg -> "${left.renderShort()} $op ${right.renderShort()}"
    is KBUnaryArg -> "$op${operand.renderShort()}"
    is KBArrowFunctionArg -> "(${params.joinToString()}) => …"
}
