package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.streams.ops.filter
import io.peekandpoke.klang.blocks.model.*
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksLetStmtComp(
    stmt: KBLetStmt,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksLetStmtComp.Props(
        stmtId = stmt.id,
        stmt = stmt,
        keyword = "let",
        name = stmt.name,
        value = stmt.value,
        ctx = ctx,
    )
) { KlangBlocksLetStmtComp(it) }

@Suppress("FunctionName")
fun Tag.KlangBlocksLetStmtComp(
    stmt: KBConstStmt,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksLetStmtComp.Props(
        stmtId = stmt.id,
        stmt = stmt,
        keyword = "const",
        name = stmt.name,
        value = stmt.value,
        ctx = ctx,
    )
) { KlangBlocksLetStmtComp(it) }

class KlangBlocksLetStmtComp(ctx: Ctx<Props>) : Component<KlangBlocksLetStmtComp.Props>(ctx) {

    data class Props(
        val stmtId: String,
        val stmt: KBStmt,
        val keyword: String,
        val name: String,
        val value: KBArgValue?,
        val ctx: KlangBlocksCtx,
    )

    private val variant = BlockVariant.TopLevel
    private var isEditing: Boolean by value(false)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)
    private var activeAtoms: Set<KlangBlockAtomKey> by value(emptySet())
    private val atomTimeouts = mutableMapOf<KlangBlockAtomKey, Int>()

    @Suppress("unused")
    private val highlightSub by subscribingTo(
        props.ctx.highlights.filter { it?.blockId == props.stmtId }
    ) { signal ->
        if (signal != null) {
            val key = KlangBlockAtomKey(signal.slotIndex, signal.atomStart, signal.atomEnd)
            atomTimeouts[key]?.let { window.clearTimeout(it) }
            activeAtoms = activeAtoms + key
            atomTimeouts[key] = window.setTimeout({
                activeAtoms = activeAtoms - key
                atomTimeouts.remove(key)
            }, signal.durationMs.toInt())
        }
    }

    private fun startEdit() {
        val currentText = when (val v = props.value) {
            is KBStringArg -> v.value
            is KBNumberArg -> {
                val l = v.value.toLong()
                if (v.value == l.toDouble()) l.toString() else v.value.toString()
            }

            else -> ""
        }
        isEditing = true
        editText = currentText
    }

    private fun commitEdit() {
        if (!isEditing) return
        val text = editText.trim()
        val arg: KBArgValue = if (text.isEmpty()) {
            KBEmptyArg("")
        } else {
            val num = text.toDoubleOrNull()
            if (num != null) KBNumberArg(num) else KBStringArg(text)
        }
        props.ctx.editing.onArgChanged(props.stmtId, 0, arg)
        isEditing = false
        editText = ""
    }

    private fun cancelEdit() {
        isEditing = false
        editText = ""
    }

    // ── Main render ────────────────────────────────────────────────────────────

    override fun VDom.render() {
        val ctx = props.ctx
        val dndState = ctx.dnd.state
        val canDropToSlot = dndState?.accepts(DropTarget.EmptySlot) == true
        val value = props.value
        val slotIsEmpty = value == null || value is KBEmptyArg
        val canDrop = canDropToSlot && slotIsEmpty

        div("kb-block") {
            css {
                display = Display.inlineFlex
                flexDirection = FlexDirection.row
                alignItems = Align.center
                gap = variant.gap
                padding = Padding(horizontal = variant.paddingH, vertical = variant.paddingV)
                borderRadius = variant.radius
                backgroundColor = Color(categoryColour("structural"))
                color = Color.white
                fontSize = variant.fontSize
                fontFamily = "monospace"
                whiteSpace = WhiteSpace.nowrap
                userSelect = UserSelect.none
                flexShrink = 0.0
                if (activeAtoms.isNotEmpty()) put("filter", "brightness(1.4)")
                put("transition", "filter 0.15s ease")
                position = Position.relative
                cursor = Cursor.grab
            }
            onMouseOver { event ->
                if (ctx.dnd.state == null) event.stopPropagation()
                isHovered = true
            }
            onMouseOut { event ->
                if (ctx.dnd.state == null) event.stopPropagation()
                isHovered = false
            }
            // Dragging the block tile itself reorders the row (same as the row-number drag handle)
            onMouseDown { event ->
                event.preventDefault()
                ctx.dnd.startCanvasDrag(
                    props.stmtId, props.stmt,
                    event.clientX.toDouble(), event.clientY.toDouble(),
                )
            }

            // "let name =" label
            span {
                css { fontWeight = FontWeight.bold }
                +"${props.keyword} ${props.name} ="
            }

            // Value slot
            when {
                value is KBNestedChainArg -> renderNestedChainSlot(value, ctx, canDrop)
                isEditing -> renderEditingSlot()
                else -> renderValueSlot(value, canDrop, ctx)
            }

            // Hover remove button
            if (isHovered) {
                renderHoverRemove(ctx)
            }
        }
    }

    // ── Nested chain slot (value is a KBNestedChainArg) ────────────────────────

    private fun DIV.renderNestedChainSlot(arg: KBNestedChainArg, ctx: KlangBlocksCtx, canDrop: Boolean) {
        renderNestedChainSlot(
            chain = arg.chain,
            canDrop = canDrop,
            blockId = props.stmtId,
            slotIndex = 0,
            ctx = ctx,
        )
    }

    // ── Inline edit input ──────────────────────────────────────────────────────

    private fun DIV.renderEditingSlot() {
        renderBlockEditInput(
            variant = variant,
            editText = editText,
            onInput = { editText = it },
            onCommit = ::commitEdit,
            onCancel = ::cancelEdit,
        )
    }

    // ── Value slot (scalar or empty) ───────────────────────────────────────────

    private fun DIV.renderValueSlot(arg: KBArgValue?, canDrop: Boolean, ctx: KlangBlocksCtx) {
        val dndState = ctx.dnd.state
        span {
            css {
                borderRadius = variant.slotRadius
                padding = Padding(horizontal = variant.slotPadH, vertical = variant.slotPadV)
                fontSize = variant.editFontSize
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
                    dndState?.onDrop?.invoke(DropDestination.EmptySlot(props.stmtId, 0))
                }
                onMouseDown { event -> event.stopPropagation() }
            } else {
                onClick { event ->
                    event.stopPropagation()
                    startEdit()
                }
                onMouseDown { event -> event.stopPropagation() }
            }
            when (arg) {
                null, is KBEmptyArg -> +"[value]"
                else -> +arg.renderShort()
            }
        }
    }

    // ── Hover remove button ────────────────────────────────────────────────────

    private fun DIV.renderHoverRemove(ctx: KlangBlocksCtx) {
        span {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                position = Position.absolute
                top = (-8).px
                right = 0.px
                backgroundColor = Color(categoryColour("structural"))
                borderTopRightRadius = 8.px
                borderTopLeftRadius = 8.px
                borderBottomLeftRadius = 6.px
                padding = Padding(2.px, 4.px)
            }
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
                    ctx.editing.onRemoveStmt(props.stmtId)
                }
                onMouseDown { event -> event.stopPropagation() }
                +"×"
            }
        }
    }
}
