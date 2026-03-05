package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.KlangSymbolDocsComp(
    symbol: KlangSymbol,
    onNavigate: (KlangSymbol, dynamic) -> Unit,
) = comp(
    KlangSymbolDocsComp.Props(symbol = symbol, onNavigate = onNavigate)
) { KlangSymbolDocsComp(it) }

class KlangSymbolDocsComp(ctx: Ctx<Props>) : Component<KlangSymbolDocsComp.Props>(ctx) {

    data class Props(
        val symbol: KlangSymbol,
        val onNavigate: (KlangSymbol, dynamic) -> Unit,
    )

    private var copiedIndex: Int? by value(null)

    override fun VDom.render() {
        val symbol = props.symbol
        val description = symbol.variants
            .firstOrNull { it.description.isNotBlank() }
            ?.description
            ?.split("\n\n")?.first()?.trim()

        val sortedVariants = symbol.variants.sortedBy {
            when (it) {
                is KlangCallable -> it.receiver?.simpleName?.length ?: 0
                is KlangProperty -> it.owner?.simpleName?.length ?: 0
            }
        }

        val firstSample = symbol.variants
            .firstOrNull { it.samples.isNotEmpty() }
            ?.samples?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

        ui.segment {
            css {
                width = LinearDimension.fitContent
                minWidth = 400.px
                maxWidth = 50.vw
            }

            onClick { event -> event.stopPropagation() }

            ui.list {
                // ── Description ───────────────────────────────────────────────────
                if (description != null) {
                    noui.item {
                        sectionLabel("Description")
                        noui.content {
                            MarkdownDisplay(description)
                        }
                    }
                }

                // ── Signatures ────────────────────────────────────────────────────
                noui.item {
                    sectionLabel("Signatures")
                    noui.content {
                        codeBlock(
                            sortedVariants.joinToString("\n") { it.signature },
                        )
                    }
                }

                // ── Examples ───────────────────────────────────────────────────────
                noui.item {
                    if (firstSample != null) {
                        sectionLabel("Example")
                        noui.content {
                            codeBlock(firstSample)
                        }
                    }
                }
            }

            // ── View docs link ────────────────────────────────────────────────
            noui.divider {}

            ui.horizontal.list {
                noui.item {
                    css {
                        cursor = Cursor.pointer
                        fontSize = 12.px
                        color = Color("#333")
                    }
                    onClick { event ->
                        event.stopPropagation()
                        props.onNavigate(symbol, event.asDynamic())
                    }
                    icon.small.book()
                    +"View docs"
                }
            }
        }
    }

    private fun FlowContent.sectionLabel(text: String) {
        ui.small.header { +text }
    }

    private fun FlowContent.codeBlock(text: String, copyToClipboard: Boolean = false) {
        div {
            css {
                position = Position.relative
                padding = Padding(6.px, 8.px)
                paddingRight = 32.px
                margin = Margin(0.px)
                backgroundColor = Color("#f8f8f8")
                border = Border(1.px, BorderStyle.solid, Color("#ddd"))
            }

            pre {
                css {
                    fontFamily = "monospace"
                    fontSize = 12.px
                    margin = Margin(0.px)
                    overflowX = Overflow.auto
                }
                +text
            }

            // Copy button
            if (copyToClipboard) {
                span {
                    css {
                        position = Position.absolute
                        top = 4.px
                        right = 4.px
                        cursor = Cursor.pointer
                        color = if (copiedIndex == 0) Color("#27ae60") else Color("#999")
                    }
                    onClick { event ->
                        event.stopPropagation()
                        window.asDynamic().navigator.clipboard.writeText(text)
                        copiedIndex = 0
                        window.setTimeout({ copiedIndex = null }, 1500)
                    }
                    if (copiedIndex == 0) icon.small.check() else icon.small.copy()
                }
            }
        }
    }
}
