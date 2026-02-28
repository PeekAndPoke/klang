package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.streams.ops.filter
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.html.*

enum class BlockVariant {
    TopLevel, Nested;

    val fontSize get() = if (this == TopLevel) 13.px else 11.px
    val editFontSize get() = if (this == TopLevel) 12.px else 11.px
    val paddingH get() = if (this == TopLevel) 10.px else 7.px
    val paddingV get() = if (this == TopLevel) 5.px else 3.px
    val radius get() = if (this == TopLevel) 8.px else 6.px
    val gap get() = if (this == TopLevel) "4px" else "3px"
    val slotRadius get() = if (this == TopLevel) 4.px else 3.px
    val slotPadH get() = if (this == TopLevel) 6.px else 4.px
    val slotPadV get() = if (this == TopLevel) 2.px else 1.px
    val textareaPadH get() = if (this == TopLevel) 4.px else 3.px
    val textareaMinW get() = if (this == TopLevel) 100.px else 80.px
    val textareaMinH get() = if (this == TopLevel) 26.px else 22.px

    val isTopLevel get() = this == TopLevel
}

@Suppress("FunctionName")
fun Tag.KlangBlocksBlockComp(
    block: KBCallBlock,
    chain: KBChainStmt,
    ctx: KlangBlocksCtx,
    variant: BlockVariant = BlockVariant.TopLevel,
) = comp(
    KlangBlocksBlockComp.Props(
        block = block,
        chain = chain,
        ctx = ctx,
        variant = variant,
    )
) {
    KlangBlocksBlockComp(it)
}

class KlangBlocksBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksBlockComp.Props>(ctx) {

    data class Props(
        val block: KBCallBlock,
        val chain: KBChainStmt,
        val ctx: KlangBlocksCtx,
        val variant: BlockVariant = BlockVariant.TopLevel,
    )

    private var editingSlotIndex: Int? by value(null)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)
    private var isActive: Boolean by value(false)
    private var highlightTimeoutId: Int = 0

    @Suppress("unused")
    private val highlightSub by subscribingTo(
        props.ctx.highlights.filter { it?.blockId == props.block.id }
    ) { signal ->
        if (signal != null) {
            window.clearTimeout(highlightTimeoutId)
            isActive = true
            highlightTimeoutId = window.setTimeout({ isActive = false }, signal.durationMs.toInt())
        }
    }

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
        val variant = props.variant
        val dndState = ctx.dnd.state
        val isVertical = block.pocketLayout == KBPocketLayout.VERTICAL
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()
        val canDropToSlot = dndState?.onDropToSlot != null

        val cssClass = if (variant.isTopLevel) "kb-block" else "kb-nested-block"
        div(cssClass) {
            css {
                display = Display.inlineFlex
                flexDirection = if (isVertical) FlexDirection.column else FlexDirection.row
                alignItems = if (isVertical) Align.flexStart else Align.center
                put("gap", variant.gap)
                padding = Padding(horizontal = variant.paddingH, vertical = variant.paddingV)
                borderRadius = variant.radius
                backgroundColor = Color(categoryColour(doc?.category))
                color = Color.white
                fontSize = variant.fontSize
                fontFamily = "monospace"
                whiteSpace = WhiteSpace.nowrap
                userSelect = UserSelect.none
                if (isActive) put("filter", "brightness(1.5)")
                put("transition", "filter 0.15s ease")
                position = Position.relative
                // Nested blocks are always draggable; top-level only when not in slot-drop mode.
                if (!variant.isTopLevel || !canDropToSlot) cursor = Cursor.grab
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            // Start a drag when pressing on the block background or function name.
            // Slot children stop mousedown propagation so they won't trigger this.
            onMouseDown { event ->
                val shouldDrag = if (variant.isTopLevel) !canDropToSlot else true
                if (shouldDrag) {
                    event.preventDefault()
                    if (!variant.isTopLevel) event.stopPropagation()
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
                // Drop-to-slot highlighting only applies to top-level blocks.
                val canDrop = canDropToSlot && slotAcceptsChainDrop(slot) && variant.isTopLevel

                if (editingSlotIndex == i) {
                    if (slot.kind.isStringish) {
                        // Multiline-capable textarea: Enter = commit, Shift+Enter = newline
                        textArea {
                            +editText
                            rows = editText.lines().size.coerceAtLeast(1).toString()
                            autoFocus = true
                            onInput { event ->
                                editText = event.asDynamic().target.value as String
                                val el = event.asDynamic().target
                                el.style.height = "auto"
                                el.style.height = "${el.scrollHeight}px"
                            }
                            onBlur { commitEdit(i) }
                            onKeyDown { event ->
                                when {
                                    event.key == "Enter" && !event.shiftKey -> {
                                        event.preventDefault(); commitEdit(i)
                                    }

                                    event.key == "Escape" -> cancelEdit()
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
                                minHeight = variant.textareaMinH
                                overflow = Overflow.hidden
                                outline = Outline.none
                                resize = Resize.none
                                put("box-sizing", "border-box")
                                put("field-sizing", "content")
                            }
                        }
                    } else {
                        input {
                            value = editText
                            autoFocus = true
                            onInput { event -> editText = event.asDynamic().target.value as String }
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
                                fontSize = variant.editFontSize
                                fontFamily = "monospace"
                                padding = Padding(horizontal = variant.textareaPadH, vertical = 1.px)
                                minWidth = variant.textareaMinW
                                outline = Outline.none
                                put("box-sizing", "border-box")
                            }
                        }
                    }
                } else if (arg is KBNestedChainArg) {
                    // Render the nested chain as inline mini-blocks for both variants.
                    div {
                        css {
                            display = Display.inlineFlex
                            alignItems = Align.center
                            put("gap", "8px")
                            borderRadius = 4.px
                            backgroundColor = Color("rgba(0,0,0,0.2)")
                            padding = Padding(horizontal = 4.px, vertical = 2.px)
                            if (canDrop) {
                                border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.5)"))
                                cursor = Cursor.copy
                            } else {
                                border = Border(1.px, BorderStyle.solid, Color.transparent)
                            }
                        }
                        if (canDrop) {
                            onMouseUp { event ->
                                event.stopPropagation()
                                dndState?.onDropToSlot?.invoke(props.chain.id, block.id, i)
                            }
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        var isFirstBlock = true
                        arg.chain.steps.forEach { nestedItem ->
                            when (nestedItem) {
                                is KBStringLiteralItem -> {
                                    KlangBlocksStringLiteralItemComp(
                                        item = nestedItem,
                                        chainId = arg.chain.id,
                                        ctx = ctx,
                                    )
                                    isFirstBlock = false
                                }

                                is KBCallBlock -> {
                                    KlangBlocksDropZoneComp(
                                        chainId = arg.chain.id,
                                        insertBeforeBlockId = nestedItem.id,
                                        ctx = ctx,
                                        showConnectorWhenIdle = !isFirstBlock,
                                    )
                                    KlangBlocksBlockComp(
                                        block = nestedItem,
                                        chain = arg.chain,
                                        ctx = ctx,
                                        variant = BlockVariant.Nested,
                                    )
                                    isFirstBlock = false
                                }

                                is KBNewlineHint -> { /* newline hint, no connector reset needed */
                                }
                            }
                        }
                        KlangBlocksDropZoneComp(
                            chainId = arg.chain.id,
                            insertBeforeBlockId = null,
                            ctx = ctx,
                        )
                    }
                } else {
                    val isMultilineString = arg is KBStringArg && '\n' in arg.value
                    span {
                        css {
                            borderRadius = variant.slotRadius
                            padding = Padding(horizontal = variant.slotPadH, vertical = variant.slotPadV)
                            fontSize = variant.editFontSize
                            if (isMultilineString) whiteSpace = WhiteSpace.preWrap
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
                            is KBStringArg -> +arg.value
                            else -> +arg.renderShort()
                        }
                    }
                }
            }

            // Layout toggle + Remove (×) — appear on hover, hidden while a slot-drop is active
            if (isHovered && (!variant.isTopLevel || !canDropToSlot)) {
                span {
                    css {
                        display = Display.inlineFlex
                        alignItems = Align.center
                        put("gap", "2px")
                        position = Position.absolute
                        if (variant.isTopLevel) {
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
                        } else {
                            top = (-7).px
                            right = 0.px
                            backgroundColor = Color(categoryColour(doc?.category))
                            borderTopRightRadius = 8.px
                            borderBottomLeftRadius = 6.px
                            padding = Padding(2.px, 4.px)
                            after {
                                content = QuotedString("")
                                position = Position.absolute
                                top = 100.pct
                                left = 0.px
                                right = 0.px
                                height = 10.px
                            }
                        }
                    }
                    // Layout toggle button
                    span {
                        css {
                            fontSize = variant.editFontSize
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
                            fontSize = variant.editFontSize
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
