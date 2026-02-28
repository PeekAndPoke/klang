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
        if (editingSlotIndex != slotIndex) return  // guard: onBlur fires again when input is removed from DOM
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
        val isVertical = block.pocketLayout == KBPocketLayout.VERTICAL
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()
        val canDropToSlot = dndState?.onDropToSlot != null

        div("kb-block") {
            css {
                display = Display.inlineFlex
                flexDirection = if (isVertical) FlexDirection.column else FlexDirection.row
                alignItems = if (isVertical) Align.flexStart else Align.center
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
            // Start a drag when pressing on the block background or function name.
            // Slot children stop mousedown propagation, so they won't trigger this.
            // Default: drag just this block. CTRL held at mousedown (or toggled during drag): drag tail.
            onMouseDown { event ->
                if (!canDropToSlot) {
                    event.preventDefault()
                    ctx.dnd.startBlockDrag(
                        sourceChainId = props.chain.id,
                        sourceChain = props.chain,
                        block = block,
                        ctrlHeld = event.ctrlKey,
                        x = event.clientX.toDouble(),
                        y = event.clientY.toDouble(),
                    )
                }
            }

            span {
                css { fontWeight = FontWeight.bold }
                +block.funcName
            }

            slots.toRenderItems(block.args).forEach { item ->
                val i = item.index
                val arg = item.arg
                val slot = item.slot
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
                } else if (arg is KBNestedChainArg) {
                    // Always render the nested chain as inline mini-blocks.
                    // KlangBlocksInlineDropZoneComp handles both the normal connector and drop-zone state.
                    div {
                        css {
                            display = Display.inlineFlex
                            alignItems = Align.center
                            put("gap", "8px")
                            borderRadius = 4.px
                            backgroundColor = Color("rgba(0,0,0,0.2)")
                            padding = Padding(horizontal = 4.px, vertical = 2.px)
                            // Show a slot-level drop highlight when the whole chain can be replaced
                            if (canDrop) {
                                border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.5)"))
                                cursor = Cursor.copy
                            } else {
                                border = Border(1.px, BorderStyle.solid, Color.transparent)
                            }
                        }
                        if (canDrop) {
                            // Dropping on the outer container replaces the whole nested chain
                            onMouseUp { event ->
                                event.stopPropagation()
                                dndState?.onDropToSlot?.invoke(props.chain.id, block.id, i)
                            }
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        var prevWasBlock = false
                        arg.chain.steps.forEach { nestedItem ->
                            when (nestedItem) {
                                is KBCallBlock -> {
                                    if (prevWasBlock) {
                                        KlangBlocksInlineDropZoneComp(
                                            chainId = arg.chain.id,
                                            insertBeforeBlockId = nestedItem.id,
                                            ctx = ctx,
                                        )
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
                        KlangBlocksChainDropZoneComp(
                            chainId = arg.chain.id,
                            ctx = ctx,
                        )
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
                                hover {
                                    backgroundColor = Color("rgba(255,255,255,0.5)")
                                    border = Border(1.px, BorderStyle.solid, Color.white)
                                }
                            } else {
                                backgroundColor = Color("rgba(0,0,0,0.2)")
                                border = Border(1.px, BorderStyle.solid, Color.transparent)
                                cursor = Cursor.text
                                if (arg == null || arg is KBEmptyArg) opacity = 0.6
                                hover {
                                    backgroundColor = Color("rgba(255,255,255,0.15)")
                                }
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

            // Layout toggle + Remove (×) — appear on hover, hidden while a drag is active
            if (isHovered && !canDropToSlot) {
                span {
                    css {
                        display = Display.inlineFlex
                        alignItems = Align.center
                        put("gap", "2px")
                        position = Position.absolute
                        if (isVertical) {
                            top = 4.px
                            right = 4.px
                        } else {
                            top = (-8).px
                            right = 0.px
                            backgroundColor = Color(categoryColour(doc?.category))
                            borderTopRightRadius = 8.px
                            borderTopLeftRadius = 8.px
                            borderBottomLeftRadius = 6.px
                            padding = Padding(2.px, 4.px)
                        }
                    }
                    // Layout toggle button
                    span {
                        css {
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
                            ctx.editing.onToggleLayout(block.id)
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        +if (isVertical) "↔" else "↕"
                    }
                    // Remove button
                    span {
                        css {
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
}

/** Returns true if a slot can accept a dropped block/chain. */
private fun slotAcceptsChainDrop(slot: KBSlot): Boolean = slot.kind.acceptsBlock

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
