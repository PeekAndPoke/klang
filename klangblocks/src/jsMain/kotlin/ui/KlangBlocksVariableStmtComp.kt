package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.*
import kotlinx.css.*
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.Element

@Suppress("FunctionName")
fun Tag.KlangBlocksVariableStmtComp(
    stmt: KBLetStmt,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksVariableStmtComp.Props(
        stmtId = stmt.id,
        stmt = stmt,
        keyword = "let",
        name = stmt.name,
        value = stmt.value,
        ctx = ctx,
    )
) { KlangBlocksVariableStmtComp(it) }

@Suppress("FunctionName")
fun Tag.KlangBlocksVariableStmtComp(
    stmt: KBConstStmt,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksVariableStmtComp.Props(
        stmtId = stmt.id,
        stmt = stmt,
        keyword = "const",
        name = stmt.name,
        value = stmt.value,
        ctx = ctx,
    )
) { KlangBlocksVariableStmtComp(it) }

class KlangBlocksVariableStmtComp(ctx: Ctx<Props>) : Component<KlangBlocksVariableStmtComp.Props>(ctx) {

    data class Props(
        val stmtId: String,
        val stmt: KBStmt,
        val keyword: String,
        val name: String,
        val value: KBArgValue?,
        val ctx: KlangBlocksCtx,
    )

    private var isEditing: Boolean by value(false)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)
    private val highlights = highlights(props.ctx.highlights) { it?.blockId == props.stmtId }

    private fun startEdit() {
        val currentText = when (val v = props.value) {
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

        div(classes = "kb-block ${ctx.theme.styles.letStmtBlock()}") {
            css {
                gap = 4.px
                padding = Padding(horizontal = 10.px, vertical = 5.px)
                borderRadius = 8.px
                fontSize = 13.px
                if (highlights.activeAtoms.isNotEmpty()) put("filter", "brightness(1.4)")
            }
            onMouseOver { event ->
                if (ctx.dnd.state == null) event.stopPropagation()
                isHovered = true
            }
            onMouseOut { event ->
                // Ignore events where the mouse moved to a child element (e.g. the hover remove button).
                val currentEl = event.currentTarget as? Element
                val relatedEl = event.relatedTarget as? Element
                if (currentEl != null && relatedEl != null && currentEl.contains(relatedEl)) return@onMouseOut
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
                +"${props.keyword} "
                span { css { opacity = 0.85 }; +"$" }
                +"${props.name} ="
            }

            // Value slot
            when {
                value is KBNestedChainArg -> renderNestedChainSlot(value, ctx, canDrop)
                value is KBStringArg && !canDrop -> KlangBlocksStringEditorComp(
                    value = value.value,
                    ctx = ctx,
                    onCommit = { ctx.editing.onArgChanged(props.stmtId, 0, KBStringArg(it)) },
                    blockId = props.stmtId,
                    slotIndex = 0,
                )
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
            theme = props.ctx.theme,
            editText = editText,
            onInput = { editText = it },
            onCommit = ::commitEdit,
            onCancel = ::cancelEdit,
        )
    }

    // ── Value slot (scalar or empty) ───────────────────────────────────────────

    private fun DIV.renderValueSlot(arg: KBArgValue?, canDrop: Boolean, ctx: KlangBlocksCtx) {
        val dndState = ctx.dnd.state
        val slotClass = if (canDrop) ctx.theme.styles.valueSlotDrop() else ctx.theme.styles.valueSlot()
        span(classes = slotClass) {
            key = "slot-0"
            css {
                borderRadius = 4.px
                padding = Padding(horizontal = 6.px, vertical = 2.px)
                fontSize = 12.px
                if (!canDrop && (arg == null || arg is KBEmptyArg)) opacity = 0.6
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
        span(classes = ctx.theme.styles.letStmtHoverRemove()) {
            key = "hover-remove"
            span(classes = ctx.theme.styles.blockActionBtn()) {
                css { fontSize = 12.px }
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
