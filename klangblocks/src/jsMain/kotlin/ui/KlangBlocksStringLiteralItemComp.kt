package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import io.peekandpoke.klang.blocks.model.KBStringLiteralItem
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
            div(classes = props.ctx.theme.styles.stringLiteralEditWrapper()) {
                textArea(classes = props.ctx.theme.styles.stringLiteralTextarea()) {
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
                }
            }
        } else {
            span(classes = props.ctx.theme.styles.inlineItem()) {
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
