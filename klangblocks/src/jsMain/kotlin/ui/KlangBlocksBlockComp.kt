package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import io.peekandpoke.klang.blocks.model.*
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBlocksBlockComp(block: KBCallBlock) =
    comp(KlangBlocksBlockComp.Props(block = block)) { KlangBlocksBlockComp(it) }

class KlangBlocksBlockComp(ctx: Ctx<Props>) : Component<KlangBlocksBlockComp.Props>(ctx) {

    data class Props(val block: KBCallBlock)

    override fun VDom.render() {
        val block = props.block
        val doc = DslDocsRegistry.global.get(block.funcName)
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
            }

            span {
                css { fontWeight = FontWeight.bold }
                +block.funcName
            }

            slots.forEachIndexed { i, slot ->
                val arg: KBArgValue? = block.args.getOrNull(i)
                span {
                    css {
                        backgroundColor = Color("rgba(0,0,0,0.2)")
                        borderRadius = 4.px
                        padding = Padding(horizontal = 6.px, vertical = 2.px)
                        fontSize = 12.px
                        if (arg == null || arg is KBEmptyArg) opacity = 0.6
                    }
                    when (arg) {
                        null, is KBEmptyArg -> +"[${slot.name}]"
                        else -> +arg.renderShort()
                    }
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
