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

    // Each active atom has its own fade-out timer so concurrent atoms don't cancel each other.
    private var activeAtoms: Set<AtomKey> by value(emptySet())
    private val atomTimeouts = mutableMapOf<AtomKey, Int>()  // non-reactive bookkeeping

    @Suppress("unused")
    private val highlightSub by subscribingTo(
        props.ctx.highlights.filter { it?.blockId == props.block.id }
    ) { signal ->
        if (signal != null) {
            val key = AtomKey(signal.slotIndex, signal.atomStart, signal.atomEnd)
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
                flexShrink = 0.0   // never let a parent flex row squeeze this block's width
                if (activeAtoms.isNotEmpty()) put("filter", "brightness(1.4)")
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
                                put("field-sizing", "content")
                            }
                        }
                    }
                } else if (arg is KBNestedChainArg) {
                    // Optional string/identifier head (always the very first step if present).
                    val headStep = arg.chain.steps.firstOrNull()
                        ?.takeIf { it is KBStringLiteralItem || it is KBIdentifierItem }
                    // Split remaining KBCallBlock steps at KBNewlineHint into segments.
                    val callSteps = if (headStep != null) arg.chain.steps.drop(1) else arg.chain.steps
                    val segments = buildList {
                        var current = mutableListOf<KBCallBlock>()
                        for (step in callSteps) {
                            when (step) {
                                is KBCallBlock -> current.add(step)
                                is KBNewlineHint -> if (current.isNotEmpty()) {
                                    add(current.toList()); current = mutableListOf()
                                }

                                else -> {}
                            }
                        }
                        if (current.isNotEmpty()) add(current.toList())
                    }
                    div {
                        css {
                            display = Display.inlineFlex
                            flexDirection = FlexDirection.column
                            put("gap", "2px")
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
                                dndState.onDropToSlot(stmtId = props.chain.id, blockId = block.id, slotIdx = i)
                            }
                        }
                        onMouseDown { event -> event.stopPropagation() }
                        var isFirstBlock = true
                        segments.forEachIndexed { segIndex, blocks ->
                            div {
                                css {
                                    display = Display.inlineFlex
                                    alignItems = Align.center
                                    put("gap", "8px")
                                }
                                // Head item (string/identifier) only in the first segment
                                if (segIndex == 0 && headStep != null) {
                                    when (headStep) {
                                        is KBStringLiteralItem -> KlangBlocksStringLiteralItemComp(
                                            item = headStep,
                                            chainId = arg.chain.id,
                                            ctx = ctx,
                                        )

                                        is KBIdentifierItem -> span {
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
                                            +headStep.name
                                        }

                                        else -> {}
                                    }
                                }
                                blocks.forEach { nestedBlock ->
                                    KlangBlocksDropZoneComp(
                                        chainId = arg.chain.id,
                                        insertBeforeBlockId = nestedBlock.id,
                                        ctx = ctx,
                                        showConnectorWhenIdle = !isFirstBlock,
                                        onToggleNewline = if (!isFirstBlock) {
                                            { ctx.editing.onToggleNewlineBeforeBlock(arg.chain.id, nestedBlock.id) }
                                        } else null,
                                    )
                                    KlangBlocksBlockComp(
                                        block = nestedBlock,
                                        chain = arg.chain,
                                        ctx = ctx,
                                        variant = BlockVariant.Nested,
                                    )
                                    isFirstBlock = false
                                }
                                // Append drop zone only after the last segment
                                if (segIndex == segments.lastIndex) {
                                    KlangBlocksDropZoneComp(
                                        chainId = arg.chain.id,
                                        insertBeforeBlockId = null,
                                        ctx = ctx,
                                    )
                                }
                            }
                        }
                        // Empty chain (no call blocks): still show the append drop zone
                        if (segments.isEmpty()) {
                            KlangBlocksDropZoneComp(
                                chainId = arg.chain.id,
                                insertBeforeBlockId = null,
                                ctx = ctx,
                            )
                        }
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
                                dndState.onDropToSlot(stmtId = props.chain.id, blockId = block.id, slotIdx = i)
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
                            is KBStringArg -> {
                                val atomRanges = activeAtoms
                                    .filter { it.slotIndex == i && it.atomStart != null && it.atomEnd != null }
                                    .map { it.atomStart!! until it.atomEnd!! }
                                    .sortedBy { it.first }
                                    .let { mergeRanges(it) }
                                renderWithHighlights(arg.value, atomRanges)
                            }

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

/** Identifies a unique active atom by its slot and char range. */
private data class AtomKey(val slotIndex: Int?, val atomStart: Int?, val atomEnd: Int?)

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
