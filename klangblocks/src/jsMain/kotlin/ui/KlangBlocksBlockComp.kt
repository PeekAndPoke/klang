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
import org.w3c.dom.Element

enum class BlockVariant {
    TopLevel, Nested;

    val fontSize get() = if (this == TopLevel) 13.px else 11.px
    val editFontSize get() = if (this == TopLevel) 12.px else 11.px
    val paddingH get() = if (this == TopLevel) 10.px else 7.px
    val paddingV get() = if (this == TopLevel) 5.px else 3.px
    val radius get() = if (this == TopLevel) 8.px else 6.px
    val gap get() = if (this == TopLevel) 4.px else 3.px
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

    // Each active atom has its own fade-out timer so concurrent atoms don't cancel each other.
    private var activeAtoms: Set<KlangBlockAtomKey> by value(emptySet())
    private val atomTimeouts = mutableMapOf<KlangBlockAtomKey, Int>()  // non-reactive bookkeeping

    @Suppress("unused")
    private val highlightSub by subscribingTo(
        props.ctx.highlights.filter { it?.blockId == props.block.id }
    ) { signal ->
        if (signal != null) {
            val key = KlangBlockAtomKey(signal.slotIndex, signal.atomStart, signal.atomEnd)
            // Cancel any existing fade-out for the same atom before restarting it.
            atomTimeouts[key]?.let { window.clearTimeout(it) }
            activeAtoms = activeAtoms + key
            atomTimeouts[key] = window.setTimeout({
                activeAtoms = activeAtoms - key
                atomTimeouts.remove(key)
            }, signal.durationMs.toInt())
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

    // ── Main render ───────────────────────────────────────────────────────────

    override fun VDom.render() {
        val block = props.block
        val ctx = props.ctx
        val variant = props.variant
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()
        val dndState = ctx.dnd.state
        val isVertical = block.pocketLayout == KBPocketLayout.VERTICAL
        val canDropToSlot = dndState?.accepts(DropTarget.EmptySlot) == true
        val canDropOnBlock = dndState?.accepts(DropTarget.ReplaceBlock) == true
        val docCategory = doc?.category

        div(if (variant.isTopLevel) "kb-block" else "kb-nested-block") {
            blockContainerStyle(variant, docCategory, isVertical, canDropToSlot, canDropOnBlock)
            blockDragHandlers(ctx, block, variant, canDropToSlot, canDropOnBlock)

            renderFuncName(block)
            slots.toRenderItems(block.args).forEach { item ->
                val slotIsEmpty = item.arg == null || item.arg is KBEmptyArg
                val canDrop = canDropToSlot && slotAcceptsChainDrop(item.slot) && variant.isTopLevel && slotIsEmpty
                renderSlotItem(item.index, item.arg, item.slot, ctx, variant, canDrop)
            }
            if (isHovered && (!variant.isTopLevel || !canDropToSlot)) {
                renderHoverActions(ctx, block, variant, docCategory, isVertical)
            }
        }
    }

    // ── Block container setup ─────────────────────────────────────────────────

    private fun DIV.blockContainerStyle(
        variant: BlockVariant,
        docCategory: String?,
        isVertical: Boolean,
        canDropToSlot: Boolean,
        canDropOnBlock: Boolean,
    ) {
        css {
            display = Display.inlineFlex
            flexDirection = if (isVertical) FlexDirection.column else FlexDirection.row
            alignItems = if (isVertical) Align.flexStart else Align.center
            gap = variant.gap
            padding = Padding(horizontal = variant.paddingH, vertical = variant.paddingV)
            borderRadius = variant.radius
            backgroundColor = Color(categoryColour(docCategory))
            color = Color.white
            fontSize = variant.fontSize
            fontFamily = "monospace"
            whiteSpace = WhiteSpace.nowrap
            userSelect = UserSelect.none
            flexShrink = 0.0   // never let a parent flex row squeeze this block's width
            if (activeAtoms.isNotEmpty()) put("filter", "brightness(1.4)")
            put("transition", "filter 0.15s ease")
            position = Position.relative
            if (canDropOnBlock) {
                if (isHovered) {
                    put("outline", "2px solid rgba(255,255,255,0.85)")
                    put("filter", "brightness(1.2)")
                } else {
                    put("outline", "1px dashed rgba(255,255,255,0.5)")
                }
                cursor = Cursor.copy
            } else if (!variant.isTopLevel || !canDropToSlot) {
                cursor = Cursor.grab
            }
        }
    }

    private fun DIV.blockDragHandlers(
        ctx: KlangBlocksCtx,
        block: KBCallBlock,
        variant: BlockVariant,
        canDropToSlot: Boolean,
        canDropOnBlock: Boolean,
    ) {
        onMouseOver { event ->
            // Only consume the event when this block is the drop target.
            // If nothing is dragging (normal hover for action buttons) also consume it,
            // so only the innermost hovered block shows actions.
            if (ctx.dnd.state == null || canDropOnBlock) event.stopPropagation()
            isHovered = true
        }
        onMouseOut { event ->
            // Ignore events where the mouse moved to a child element (e.g. the hover action buttons).
            val currentEl = event.currentTarget as? Element
            val relatedEl = event.relatedTarget as? Element
            if (currentEl != null && relatedEl != null && currentEl.contains(relatedEl)) return@onMouseOut
            if (ctx.dnd.state == null || canDropOnBlock) event.stopPropagation()
            isHovered = false
        }
        // Accept a replace-block drop when the drag is released over this block.
        if (canDropOnBlock) {
            val dndState = ctx.dnd.state
            onMouseUp { event ->
                event.stopPropagation()
                dndState?.onDrop?.invoke(DropDestination.ReplaceBlock(block.id))
            }
        }
        // Start a drag when pressing on the block background or function name.
        // Slot children stop mousedown propagation so they won't trigger this.
        onMouseDown { event ->
            val shouldDrag = if (variant.isTopLevel) !canDropToSlot && !canDropOnBlock else true
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
    }

    // ── Function name ─────────────────────────────────────────────────────────

    private fun DIV.renderFuncName(block: KBCallBlock) {
        span {
            css {
                minWidth = 30.px
                fontWeight = FontWeight.bold
            }
            +block.funcName
        }
    }

    // ── Argument slots ────────────────────────────────────────────────────────

    private fun DIV.renderSlotItem(
        index: Int,
        arg: KBArgValue?,
        slot: KBSlot,
        ctx: KlangBlocksCtx,
        variant: BlockVariant,
        canDrop: Boolean,
    ) {
        when {
            editingSlotIndex == index -> renderEditingSlot(index, slot, variant)
            arg is KBNestedChainArg -> renderNestedChainSlot(index, arg, ctx, canDrop)
            else -> renderValueSlot(index, arg, slot, ctx, variant, canDrop)
        }
    }

    private fun DIV.renderEditingSlot(index: Int, slot: KBSlot, variant: BlockVariant) {
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
                onBlur { commitEdit(index) }
                onKeyDown { event ->
                    when (event.key) {
                        "Enter" if !event.shiftKey -> {
                            event.preventDefault(); commitEdit(index)
                        }

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
                    minHeight = variant.textareaMinH
                    overflow = Overflow.hidden
                    outline = Outline.none
                    resize = Resize.none
                    put("box-sizing", "border-box")
                    put("field-sizing", "content")
                }
            }
        } else {
            renderBlockEditInput(
                variant = variant,
                editText = editText,
                onInput = { editText = it },
                onCommit = { commitEdit(index) },
                onCancel = ::cancelEdit,
            )
        }
    }

    private fun DIV.renderNestedChainSlot(
        index: Int,
        arg: KBNestedChainArg,
        ctx: KlangBlocksCtx,
        canDrop: Boolean,
    ) {
        renderNestedChainSlot(
            chain = arg.chain,
            canDrop = canDrop,
            blockId = props.block.id,
            slotIndex = index,
            ctx = ctx,
        )
    }

    private fun DIV.renderValueSlot(
        index: Int,
        arg: KBArgValue?,
        slot: KBSlot,
        ctx: KlangBlocksCtx,
        variant: BlockVariant,
        canDrop: Boolean,
    ) {
        val dndState = ctx.dnd.state
        val isMultilineString = arg is KBStringArg && '\n' in arg.value
        span {
            css {
                borderRadius = variant.slotRadius
                padding = Padding(horizontal = variant.slotPadH, vertical = variant.slotPadV)
                fontSize = variant.editFontSize
                if (isMultilineString) whiteSpace = WhiteSpace.preWrap
                if (canDrop) {
                    backgroundColor = Color("rgba(255,255,255,0.08)")
                    border = Border(1.px, BorderStyle.dashed, Color("rgba(255,255,255,0.5)"))
                    cursor = Cursor.copy
                    hover {
                        backgroundColor = Color("rgba(255,255,255,0.45)")
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
                onMouseOver { event -> event.stopPropagation() }
                onMouseUp { event ->
                    event.stopPropagation()
                    dndState?.onDrop?.invoke(DropDestination.EmptySlot(props.block.id, index))
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
                    startEdit(index, currentText)
                }
                onMouseDown { event -> event.stopPropagation() }
            }
            when (arg) {
                null, is KBEmptyArg -> +"[${slot.name}]"
                is KBStringArg -> {
                    val atomRanges = activeAtoms
                        .filter { it.slotIndex == index && it.atomStart != null && it.atomEnd != null }
                        .map { it.atomStart!! until it.atomEnd!! }
                        .sortedBy { it.first }
                        .let { mergeRanges(it) }
                    renderWithHighlights(arg.value, atomRanges)
                }

                is KBIdentifierArg -> {
                    span { css { opacity = 0.85 }; +"$" }
                    +arg.name
                }

                else -> +arg.renderShort()
            }
        }
    }

    // ── Hover actions ─────────────────────────────────────────────────────────

    private fun DIV.renderHoverActions(
        ctx: KlangBlocksCtx,
        block: KBCallBlock,
        variant: BlockVariant,
        docCategory: String?,
        isVertical: Boolean,
    ) {
        span {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                gap = 2.px
                position = Position.absolute
                if (variant.isTopLevel) {
                    if (isVertical) {
                        top = 4.px
                        right = 4.px
                    } else {
                        top = (-8).px
                        right = 0.px
                        backgroundColor = Color(categoryColour(docCategory))
                        borderTopRightRadius = 8.px
                        borderTopLeftRadius = 8.px
                        borderBottomLeftRadius = 6.px
                        padding = Padding(2.px, 4.px)
                    }
                } else {
                    top = (-7).px
                    right = 0.px
                    backgroundColor = Color(categoryColour(docCategory))
                    borderTopRightRadius = 8.px
                    borderTopLeftRadius = 8.px
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

            layoutToggleButton(ctx, block, variant, isVertical)
            removeBlockButton(ctx, variant)
        }
    }

    private fun SPAN.layoutToggleButton(
        ctx: KlangBlocksCtx,
        block: KBCallBlock,
        variant: BlockVariant,
        isVertical: Boolean,
    ) {
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
    }

    private fun SPAN.removeBlockButton(
        ctx: KlangBlocksCtx,
        variant: BlockVariant,
    ) {
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

/**
 * Merges overlapping or adjacent [IntRange]s in an already-sorted list.
 * Ranges use inclusive [IntRange.last] (i.e. built with `start until end`).
 */
private fun mergeRanges(sorted: List<IntRange>): List<IntRange> {
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf(sorted[0])
    for (r in sorted.drop(1)) {
        val last = result.last()
        if (r.first <= last.last + 1) {
            result[result.lastIndex] = last.first..maxOf(last.last, r.last)
        } else {
            result.add(r)
        }
    }
    return result
}

/**
 * Renders [text] as a mix of plain text nodes and highlighted `<span>`s for each
 * range in [ranges] (sorted, non-overlapping, [IntRange.last] is inclusive).
 */
private fun HtmlInlineTag.renderWithHighlights(text: String, ranges: List<IntRange>) {
    if (ranges.isEmpty()) {
        +text
        return
    }
    var pos = 0
    for (range in ranges) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (pos < start) +text.substring(pos, start)
        span {
            css {
                borderRadius = 2.px
                // box-shadow renders like a border but occupies no space — no wiggle
                put("box-shadow", "0 0 0 2px rgba(255,255,255,0.65)")
            }
            +text.substring(start, end)
        }
        pos = end
    }
    if (pos < text.length) +text.substring(pos)
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
    is KBUnaryArg -> if (position == KBUnaryPosition.POSTFIX) "${operand.renderShort()}$op" else "$op${operand.renderShort()}"
    is KBArrowFunctionArg -> "(${params.joinToString()}) => …"
    is KBTernaryArg -> "${condition.renderShort()} ? ${thenExpr.renderShort()} : ${elseExpr.renderShort()}"
    is KBIndexAccessArg -> "${obj.renderShort()}[${index.renderShort()}]"
}
