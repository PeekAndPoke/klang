package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import kotlinx.css.*
import kotlinx.html.*
import org.w3c.dom.Element

@Suppress("FunctionName")
fun Tag.KlangBlocksBlockComp(
    block: KBCallBlock,
    chain: KBChainStmt,
    ctx: KlangBlocksCtx,
    isTopLevel: Boolean = true,
) = comp(
    KlangBlocksBlockComp.Props(
        block = block,
        chain = chain,
        ctx = ctx,
        isTopLevel = isTopLevel,
    )
) {
    KlangBlocksBlockComp(it)
}

class KlangBlocksBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksBlockComp.Props>(ctx) {

    data class Props(
        val block: KBCallBlock,
        val chain: KBChainStmt,
        val ctx: KlangBlocksCtx,
        val isTopLevel: Boolean = true,
    )

    private var editingSlotIndex: Int? by value(null)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)

    private val highlights = highlights(props.ctx.highlights) { it?.blockId == props.block.id }

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
        val isTopLevel = props.isTopLevel
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()
        val dndState = ctx.dnd.state
        val isVertical = block.pocketLayout == KBPocketLayout.VERTICAL
        val canDropToSlot = dndState?.accepts(DropTarget.EmptySlot) == true
        val canDropOnBlock = dndState?.accepts(DropTarget.ReplaceBlock) == true
        val docCategory = doc?.category

        div("kb-block ${ctx.theme.styles.blockBase()}") {
            key = "block-${block.id}"

            blockContainerStyle(isTopLevel, docCategory, isVertical, canDropToSlot, canDropOnBlock)
            blockDragHandlers(ctx, block, isTopLevel, canDropToSlot, canDropOnBlock)

            renderFuncName(block)
            slots.toRenderItems(block.args).forEachIndexed { index, item ->
                div {
                    key = "slot-$index"
                    debugId("slot-$index")

                    val slotIsEmpty = item.arg == null || item.arg is KBEmptyArg
                    val canDrop = canDropToSlot && slotAcceptsChainDrop(item.slot) && isTopLevel && slotIsEmpty
                    renderSlotItem(item.index, item.arg, item.slot, ctx, canDrop)
                }
            }
            if (isHovered && (!isTopLevel || !canDropToSlot)) {
                renderHoverActions(ctx, block, docCategory, isVertical)
            }
        }
    }

    // ── Block container setup ─────────────────────────────────────────────────

    private fun DIV.blockContainerStyle(
        isTopLevel: Boolean,
        docCategory: String?,
        isVertical: Boolean,
        canDropToSlot: Boolean,
        canDropOnBlock: Boolean,
    ) {
        css {
            flexDirection = if (isVertical) FlexDirection.column else FlexDirection.row
            alignItems = if (isVertical) Align.flexStart else Align.center
            gap = 4.px
            padding = Padding(horizontal = 10.px, vertical = 5.px)
            borderRadius = 8.px
            backgroundColor = Color(props.ctx.theme.blockColor(docCategory))
            fontSize = 13.px
            if (highlights.activeAtoms.isNotEmpty()) put("filter", "brightness(1.4)")
            if (canDropOnBlock) {
                if (isHovered) {
                    put("outline", "2px solid ${props.ctx.theme.blockDropHoverOutline}")
                    put("filter", "brightness(1.2)")
                } else {
                    put("outline", "1px dashed ${props.ctx.theme.blockDropIdleOutline}")
                }
                cursor = Cursor.copy
            } else if (!isTopLevel || !canDropToSlot) {
                cursor = Cursor.grab
            }
        }
    }

    private fun DIV.blockDragHandlers(
        ctx: KlangBlocksCtx,
        block: KBCallBlock,
        isTopLevel: Boolean,
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
            val shouldDrag = if (isTopLevel) !canDropToSlot && !canDropOnBlock else true
            if (shouldDrag) {
                event.preventDefault()
                if (!isTopLevel) event.stopPropagation()
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
        span(classes = props.ctx.theme.styles.blockFuncName()) {
            key = "func-name"
            +block.funcName
        }
    }

    // ── Argument slots ────────────────────────────────────────────────────────

    private fun DIV.renderSlotItem(
        index: Int,
        arg: KBArgValue?,
        slot: KBSlot,
        ctx: KlangBlocksCtx,
        canDrop: Boolean,
    ) {
        when {
            editingSlotIndex == index && arg !is KBStringArg -> renderEditingSlot(index, slot, ctx)
            arg is KBNestedChainArg -> renderNestedChainSlot(index, arg, ctx, canDrop)
            arg is KBStringArg && !canDrop -> KlangBlocksStringEditorComp(
                value = arg.value,
                ctx = ctx,
                onCommit = {
                    ctx.editing.onArgChanged(props.block.id, index, KBStringArg(it))
                },
                blockId = props.block.id,
                slotIndex = index,
            )

            else -> renderValueSlot(index, arg, slot, ctx, canDrop)
        }
    }

    private fun DIV.renderEditingSlot(index: Int, slot: KBSlot, ctx: KlangBlocksCtx) {
        if (slot.kind.isStringish) {
            KlangBlocksStringEditorComp(
                value = editText,
                ctx = ctx,
                onCommit = { text ->
                    if (editingSlotIndex == index) {
                        val trimmed = text.trim()
                        val arg = if (trimmed.isEmpty()) KBEmptyArg("") else KBStringArg(trimmed)
                        props.ctx.editing.onArgChanged(props.block.id, index, arg)
                        editingSlotIndex = null
                        editText = ""
                    }
                },
                onCancel = ::cancelEdit,
            )
        } else {
            renderBlockEditInput(
                theme = ctx.theme,
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
        canDrop: Boolean,
    ) {
        val dndState = ctx.dnd.state
        val isMultilineString = arg is KBStringArg && '\n' in arg.value
        val slotClass = if (canDrop) ctx.theme.styles.valueSlotDrop() else ctx.theme.styles.valueSlot()

        span(classes = slotClass) {
            key = "slot-$index"
            css {
                borderRadius = 4.px
                padding = Padding(horizontal = 6.px, vertical = 2.px)
                fontSize = 12.px
                if (isMultilineString) whiteSpace = WhiteSpace.preWrap
                if (!canDrop && (arg == null || arg is KBEmptyArg)) opacity = 0.6
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
                    val atomRanges = highlights.activeAtoms
                        .filter { it.slotIndex == index && it.atomStart != null && it.atomEnd != null }
                        .map { it.atomStart!! until it.atomEnd!! }
                        .sortedBy { it.first }
                        .let { mergeRanges(it) }
                    renderWithHighlights(arg.value, atomRanges, ctx.theme.styles)
                }

                is KBIdentifierArg -> {
                    span(classes = ctx.theme.styles.identifierDollarSign()) { +"$" }
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
        docCategory: String?,
        isVertical: Boolean,
    ) {
        span(classes = ctx.theme.styles.blockHoverActions()) {
            key = "hover-actions"
            css {
                backgroundColor = Color(ctx.theme.blockColor(docCategory))
                top = if (isVertical) 0.px else (-10).px
            }

            layoutToggleButton(ctx, block, isVertical)
            removeBlockButton(ctx)
        }
    }

    private fun SPAN.layoutToggleButton(
        ctx: KlangBlocksCtx,
        block: KBCallBlock,
        isVertical: Boolean,
    ) {
        span(classes = ctx.theme.styles.blockHoverActionBtn()) {
            css { fontSize = 12.px }
            onClick { event ->
                event.stopPropagation()
                ctx.editing.onToggleLayout(block.id)
            }
            onMouseDown { event -> event.stopPropagation() }

            if (isVertical) {
                icon.small.arrows_alternate_horizontal()
            } else {
                icon.small.arrows_alternate_vertical()
            }
        }
    }

    private fun SPAN.removeBlockButton(ctx: KlangBlocksCtx) {
        span(classes = ctx.theme.styles.blockHoverActionBtn()) {
            css { fontSize = 12.px }
            onClick { event ->
                event.stopPropagation()
                ctx.editing.onRemoveBlock(props.block.id)
            }
            onMouseDown { event -> event.stopPropagation() }
            icon.small.times()
        }
    }
}

/**
 * Merges overlapping or adjacent [IntRange]s in an already-sorted list.
 * Ranges use inclusive [IntRange.last] (i.e. built with `start until end`).
 */
internal fun mergeRanges(sorted: List<IntRange>): List<IntRange> {
    if (sorted.isEmpty()) {
        return emptyList()
    }

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
internal fun HtmlInlineTag.renderWithHighlights(text: String, ranges: List<IntRange>, styles: KlangBlocksTheme.Styles) {
    if (ranges.isEmpty()) {
        +text
        return
    }

    var pos = 0

    for (range in ranges) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (pos < start) +text.substring(pos, start)
        // box-shadow renders like a border but occupies no space — no wiggle
        span(classes = styles.highlightAtom()) {
            +text.substring(start, end)
        }
        pos = end
    }

    if (pos < text.length) {
        +text.substring(pos)
    }
}

/** Returns true if a slot can accept a dropped block/chain. */
private fun slotAcceptsChainDrop(slot: KBSlot): Boolean = slot.kind.acceptsBlock

internal fun KBArgValue.renderShort(): String = when (this) {
    is KBEmptyArg ->
        ""

    is KBStringArg ->
        "\"$value\""

    is KBNumberArg ->
        value.toString()

    is KBBoolArg ->
        value.toString()

    is KBIdentifierArg ->
        name

    is KBNestedChainArg ->
        chain.steps.filterIsInstance<KBCallBlock>().joinToString(".") { it.funcName }

    is KBBinaryArg ->
        "${left.renderShort()} $op ${right.renderShort()}"

    is KBUnaryArg ->
        if (position == KBUnaryPosition.POSTFIX) {
            "${operand.renderShort()}$op"
        } else {
            "$op${operand.renderShort()}"
        }

    is KBArrowFunctionArg ->
        "(${params.joinToString()}) => …"

    is KBTernaryArg ->
        "${condition.renderShort()} ? ${thenExpr.renderShort()} : ${elseExpr.renderShort()}"

    is KBIndexAccessArg ->
        "${obj.renderShort()}[${index.renderShort()}]"
}
