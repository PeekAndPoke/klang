package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import io.peekandpoke.klang.blocks.model.KBStringLiteralItem
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.textArea

@Suppress("FunctionName")
fun Tag.KlangBlocksStringLiteralItemComp(
    item: KBStringLiteralItem,
    chainId: String,
    ctx: KlangBlocksCtx,
) = comp(
    KlangBlocksStringLiteralItemComp.Props(item = item, chainId = chainId, ctx = ctx)
) {
    KlangBlocksStringLiteralItemComp(it)
}

class KlangBlocksStringLiteralItemComp(ctx: Ctx<Props>) : Component<KlangBlocksStringLiteralItemComp.Props>(ctx) {

    data class Props(
        val item: KBStringLiteralItem,
        val chainId: String,
        val ctx: KlangBlocksCtx,
    )

    private var isEditing: Boolean by value(false)
    private var editText: String by value("")

    private fun startEdit() {
        isEditing = true
        editText = props.item.value
    }

    private fun commitEdit() {
        props.ctx.editing.onStringLiteralItemChanged(props.chainId, editText)
        isEditing = false
        editText = ""
    }

    private fun cancelEdit() {
        isEditing = false
        editText = ""
    }

    override fun VDom.render() {
        if (isEditing) {
            div {
                css {
                    display = Display.inlineFlex
                    position = Position.relative
                }
                textArea {
                    +editText
                    autoFocus = true
                    onInput { event -> editText = event.asDynamic().target.value as String }
                    onBlur { commitEdit() }
                    onKeyDown { event ->
                        when {
                            event.key == "Enter" && !event.shiftKey -> {
                                event.preventDefault()
                                commitEdit()
                            }

                            event.key == "Escape" -> cancelEdit()
                        }
                    }
                    onMouseDown { event -> event.stopPropagation() }
                    css {
                        backgroundColor = Color(props.ctx.theme.inputBackground)
                        border = Border(1.px, BorderStyle.solid, Color(props.ctx.theme.inputBorder))
                        borderRadius = 3.px
                        color = Color(props.ctx.theme.textPrimary)
                        fontSize = 11.px
                        fontFamily = "monospace"
                        padding = Padding(horizontal = 4.px, vertical = 2.px)
                        minWidth = 60.px
                        maxWidth = 200.px
                        minHeight = 24.px
                        outline = Outline.none
                        resize = Resize.none
                        put("box-sizing", "border-box")
                        put("field-sizing", "content") // auto-grow in browsers that support it
                    }
                }
            }
        } else {
            span {
                css {
                    borderRadius = 3.px
                    padding = Padding(horizontal = 4.px, vertical = 1.px)
                    fontSize = 11.px
                    backgroundColor = Color(props.ctx.theme.inlineItemBackground)
                    border = Border(1.px, BorderStyle.solid, Color(props.ctx.theme.inlineItemBorder))
                    color = Color(props.ctx.theme.inlineItemText)
                    cursor = Cursor.text
                    fontFamily = "monospace"
                    whiteSpace = WhiteSpace.nowrap
                    hover {
                        backgroundColor = Color(props.ctx.theme.inlineItemHoverBackground)
                    }
                }
                onClick { event ->
                    event.stopPropagation()
                    startEdit()
                }
                onMouseDown { event -> event.stopPropagation() }
                // Show return symbol for newlines in the display
                val display = "\"${props.item.value.replace("\n", "↵").replace("\r", "")}\""
                +display
            }
        }
    }
}
