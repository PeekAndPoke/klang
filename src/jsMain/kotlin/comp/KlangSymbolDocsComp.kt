package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onContextMenu
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.*

@Suppress("FunctionName")
fun Tag.KlangSymbolDocsComp(
    symbol: KlangSymbol,
    onNavigate: (KlangSymbol, dynamic) -> Unit,
) = comp(
    KlangSymbolDocsComp.Props(
        symbol = symbol,
        onNavigate = onNavigate,
    )
) { KlangSymbolDocsComp(it) }

class KlangSymbolDocsComp(ctx: Ctx<Props>) : Component<KlangSymbolDocsComp.Props>(ctx) {

    data class Props(
        val symbol: KlangSymbol,
        val onNavigate: (KlangSymbol, dynamic) -> Unit,
    )

    private val laf by subscribingTo(KlangTheme)
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
            ?.takeIf { it.code.isNotBlank() }

        (ui.segment + laf.styles.docsPopup()) {
            css {
                width = LinearDimension.fitContent
                minWidth = 20.vw
                maxWidth = 50.vw
                backgroundColor = Color(laf.overlayBackground)
                color = Color(laf.textPrimary)
                borderColor = Color(laf.textTertiary)
                put("box-shadow", "none")
            }

            onClick { event -> event.stopPropagation() }
            onContextMenu { event -> event.stopPropagation() }

            ui.relaxed.list {
                noui.item {
                    sectionLabel(symbol.name)
                    noui.content {
                        if (symbol.library.isBlank()) {
                            +"Built-in"
                        } else {
                            +"Library: ${symbol.library}"
                        }
                    }
                }

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

                // ── Aliases ───────────────────────────────────────────────────────
                if (symbol.aliases.isNotEmpty()) {
                    noui.item {
                        sectionLabel("Aliases")
                        noui.content {
                            ui.horizontal.list {
                                symbol.aliases.forEach { alias ->
                                    noui.item {
                                        +alias
                                    }
                                }
                            }
                        }
                    }
                }
                // ── Examples ───────────────────────────────────────────────────────
                noui.item {
                    if (firstSample != null) {
                        sectionLabel("Example")
                        noui.content {
                            codeBlock(firstSample.code, copyToClipboard = true)
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
                        color = Color(laf.gold)
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
        ui.small.header {
            css { marginBottom = 4.px }
            b { +text }
        }
    }

    private fun FlowContent.codeBlock(text: String, copyToClipboard: Boolean = false) {
        div(classes = laf.styles.darken20()) {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = Align.flexStart
                gap = 8.px
                padding = Padding(6.px, 8.px)
                margin = Margin(0.px)
                border = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                put("border-radius", "var(--klang-border-radius)")
            }

            // Copy button
            if (copyToClipboard) {
                div {
                    css {
                        flexShrink = 0.0
                        cursor = Cursor.pointer
                        color = if (copiedIndex == 0) {
                            Color(laf.good)
                        } else {
                            Color(laf.textTertiary)
                        }
                    }
                    onClick { event ->
                        event.stopPropagation()
                        window.asDynamic().navigator.clipboard.writeText(text)
                        copiedIndex = 0
                        window.setTimeout({ copiedIndex = null }, 2000)
                    }

                    if (copiedIndex == 0) {
                        icon.small.check()
                    } else {
                        icon.small.copy()
                    }
                }
            }

            pre {
                css {
                    flex = Flex(1.0, 1.0, FlexBasis.auto)
                    fontFamily = "monospace"
                    fontSize = 12.px
                    margin = Margin(0.px)
                    overflowX = Overflow.auto
                }
                +text
            }
        }
    }
}
