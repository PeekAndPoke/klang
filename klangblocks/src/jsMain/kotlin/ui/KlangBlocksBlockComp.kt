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
    onArgChanged: (slotIndex: Int, arg: KBArgValue) -> Unit,
    onRemove: () -> Unit,
) = comp(KlangBlocksBlockComp.Props(block = block, onArgChanged = onArgChanged, onRemove = onRemove)) {
    KlangBlocksBlockComp(it)
}

class KlangBlocksBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksBlockComp.Props>(ctx) {

    data class Props(
        val block: KBCallBlock,
        val onArgChanged: (slotIndex: Int, arg: KBArgValue) -> Unit,
        val onRemove: () -> Unit,
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
        props.onArgChanged(slotIndex, arg)
        editingSlotIndex = null
        editText = ""
    }

    private fun cancelEdit() {
        editingSlotIndex = null
        editText = ""
    }

    override fun VDom.render() {
        val block = props.block
        val doc = KlangDocsRegistry.global.get(block.funcName)
        val slots = if (doc != null) KBTypeMapping.slotsFor(doc) else emptyList()

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
            }
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }

            span {
                css { fontWeight = FontWeight.bold }
                +block.funcName
            }

            slots.forEachIndexed { i, slot ->
                val arg: KBArgValue? = block.args.getOrNull(i)

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
                } else {
                    span {
                        css {
                            backgroundColor = Color("rgba(0,0,0,0.2)")
                            borderRadius = 4.px
                            padding = Padding(horizontal = 6.px, vertical = 2.px)
                            fontSize = 12.px
                            cursor = Cursor.text
                            if (arg == null || arg is KBEmptyArg) opacity = 0.6
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

            // Remove (×) — appears on hover
            if (isHovered) {
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
                        props.onRemove()
                    }
                    onMouseDown { event -> event.stopPropagation() }
                    +"×"
                }
            }
        }
    }
}

private fun KBArgValue.renderShort(): String = when (this) {
    is KBEmptyArg -> ""
    is KBStringArg -> "\"$value\""
    is KBNumberArg -> value.toString()
    is KBBoolArg -> value.toString()
    is KBIdentifierArg -> name
    is KBNestedChainArg -> "…"
    is KBBinaryArg -> "${left.renderShort()} $op ${right.renderShort()}"
    is KBUnaryArg -> "$op${operand.renderShort()}"
    is KBArrowFunctionArg -> "(${params.joinToString()}) => …"
}

internal fun categoryColour(category: String?): String = when (category) {
    "synthesis" -> "#4a6fa5"
    "sample" -> "#3a8a4a"
    "effects" -> "#3a7a3a"
    "tempo" -> "#8a7a20"
    "structural" -> "#7a3a8a"
    "random" -> "#8a3a20"
    "tonal" -> "#4a3a8a"
    "continuous" -> "#2a7a7a"
    "filters" -> "#2a6a3a"
    else -> "#555"
}
