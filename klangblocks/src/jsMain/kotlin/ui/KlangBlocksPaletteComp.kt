package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangBlocksPaletteComp(
    onDragStart: (funcName: String, x: Double, y: Double) -> Unit,
) = comp(KlangBlocksPaletteComp.Props(onDragStart = onDragStart)) {
    KlangBlocksPaletteComp(it)
}

class KlangBlocksPaletteComp(ctx: Ctx<Props>) : Component<KlangBlocksPaletteComp.Props>(ctx) {

    data class Props(
        val onDragStart: (funcName: String, x: Double, y: Double) -> Unit,
    )

    override fun VDom.render() {
        val registry = KlangDocsRegistry.global

        div {
            css {
                width = 200.px
                flexShrink = 0.0
                overflowY = Overflow.auto
                backgroundColor = Color("#252535")
                put("border-right", "1px solid #333")
                padding = Padding(8.px)
            }

            registry.categories.forEach { category ->
                val funcs = registry.getByCategory(category)
                    .filter { hasVisibleBlock(it) }

                if (funcs.isEmpty()) return@forEach

                // Category header
                div {
                    css {
                        color = Color(categoryColour(category))
                        fontSize = 10.px
                        fontWeight = FontWeight.bold
                        textTransform = TextTransform.uppercase
                        letterSpacing = LinearDimension("0.08em")
                        padding = Padding(vertical = 6.px, horizontal = 4.px)
                        marginTop = 4.px
                    }
                    +category
                }

                // Function pills
                funcs.forEach { doc ->
                    div {
                        css {
                            display = Display.block
                            backgroundColor = Color(categoryColour(category))
                            color = Color.white
                            borderRadius = 6.px
                            padding = Padding(vertical = 4.px, horizontal = 8.px)
                            marginBottom = 3.px
                            fontSize = 12.px
                            fontFamily = "monospace"
                            cursor = Cursor.grab
                            userSelect = UserSelect.none
                            whiteSpace = WhiteSpace.nowrap
                            overflow = Overflow.hidden
                            textOverflow = TextOverflow.ellipsis
                        }
                        onMouseDown { event ->
                            event.preventDefault()
                            props.onDragStart(
                                doc.name,
                                event.clientX.toDouble(),
                                event.clientY.toDouble(),
                            )
                        }
                        +doc.name
                    }
                }
            }
        }
    }
}

private fun hasVisibleBlock(doc: KlangSymbol): Boolean =
    doc.variants.any { it.signatureModel.params != null }
