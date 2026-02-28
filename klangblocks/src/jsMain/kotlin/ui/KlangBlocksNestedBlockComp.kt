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
fun Tag.KlangBlocksNestedBlockComp(
    block: KBCallBlock,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksNestedBlockComp.Props(
        block = block,
        ctx = ctx,
    )
) {
    KlangBlocksNestedBlockComp(it)
}

class KlangBlocksNestedBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksNestedBlockComp.Props>(ctx) {

    data class Props(
        val block: KBCallBlock,
        val ctx: KlangBlocksCtx,
    )

    private var editingSlotIndex: Int? by value(null)
    private var editText: String by value("")
    private var isHovered: Boolean by value(false)

    private fun startEdit(slotIndex: Int, currentText: String) {
        editingSlotIndex = slotIndex
        editText = currentText
    }

    private fun commitEdit(nestedSlotIndex: Int) {
        if (editingSlotIndex != nestedSlotIndex) return  // guard: onBlur fires again when input is removed from DOM
        val text = editText.trim()
        val arg: KBArgValue = if (text.isEmpty()) {
            KBEmptyArg("")
        } else {
            val num = text.toDoubleOrNull()
            if (num != null) KBNumberArg(num) else KBStringArg(text)
        }
        props.ctx.editing.onArgChanged(props.block.id, nestedSlotIndex, arg)
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
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()

        div("kb-nested-block") {
            css {
                display = Display.inlineFlex
                alignItems = Align.center
                put("gap", "3px")
                padding = Padding(horizontal = 7.px, vertical = 3.px)
                borderRadius = 6.px
                backgroundColor = Color(categoryColour(doc?.category))
                color = Color.white
                fontSize = 11.px
                fontFamily = "monospace"
                whiteSpace = WhiteSpace.nowrap
                userSelect = UserSelect.none
                position = Position.relative
                cursor = Cursor.grab
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            onMouseDown { event ->
                event.preventDefault()
                event.stopPropagation()
                ctx.dnd.startNestedBlockDrag(block, event.clientX.toDouble(), event.clientY.toDouble())
            }

            span {
                css { fontWeight = FontWeight.bold }
                +block.funcName
            }

            slots.toRenderItems(block.args).forEach { item ->
                val i = item.index
                val arg = item.arg
                val slot = item.slot

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
                            fontSize = 11.px
                            fontFamily = "monospace"
                            padding = Padding(horizontal = 3.px, vertical = 1.px)
                            minWidth = 40.px
                            maxWidth = 100.px
                            outline = Outline.none
                            put("box-sizing", "border-box")
                        }
                    }
                } else {
                    span {
                        css {
                            borderRadius = 3.px
                            padding = Padding(horizontal = 4.px, vertical = 1.px)
                            fontSize = 11.px
                            backgroundColor = Color("rgba(0,0,0,0.2)")
                            cursor = Cursor.text
                            if (arg == null || arg is KBEmptyArg) opacity = 0.6
                            hover {
                                backgroundColor = Color("rgba(255,255,255,0.15)")
                            }
                        }
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
                        when (arg) {
                            null, is KBEmptyArg -> +"[${slot.name}]"
                            else -> +arg.renderShort()
                        }
                    }
                }
            }

            // Remove × — appears on hover
            if (isHovered) {
                span {
                    css {
                        marginLeft = 3.px
                        fontSize = 10.px
                        lineHeight = LineHeight("1")
                        color = Color("rgba(255,255,255,0.55)")
                        cursor = Cursor.pointer
                        borderRadius = 3.px
                        padding = Padding(horizontal = 2.px, vertical = 1.px)
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
