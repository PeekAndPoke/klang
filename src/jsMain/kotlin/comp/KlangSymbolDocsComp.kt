package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onContextMenu
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.browser.window
import kotlinx.css.Align
import kotlinx.css.Border
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Flex
import kotlinx.css.FlexBasis
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.Margin
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.border
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flex
import kotlinx.css.flexDirection
import kotlinx.css.flexShrink
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.gap
import kotlinx.css.margin
import kotlinx.css.marginBottom
import kotlinx.css.maxWidth
import kotlinx.css.minWidth
import kotlinx.css.overflowX
import kotlinx.css.padding
import kotlinx.css.px
import kotlinx.css.vw
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.b
import kotlinx.html.div
import kotlinx.html.pre
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.tr

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

        ui.segment.with(laf.styles.popup()).with(laf.styles.docsPopup()) {
            css {
                width = LinearDimension.fitContent
                minWidth = 20.vw
                maxWidth = 50.vw
            }

            onClick { event -> event.stopPropagation() }
            onContextMenu { event -> event.stopPropagation() }

            ui.three.column.grid {
                noui.middle.aligned.column {
                    ui.header { +symbol.name }
                }

                noui.middle.aligned.column {
                    if (symbol.aliases.isNotEmpty()) {
                        ui.horizontal.list {
                            noui.item { +"Alias:" }
                            symbol.aliases.forEach { alias ->
                                noui.item { +alias }
                            }
                        }
                    }
                }

                noui.middle.aligned.right.aligned.column {
                    ui.basic.label {
                        if (symbol.library.isBlank()) {
                            +"Built-in"
                        } else {
                            icon.book()
                            +symbol.library.uppercase()
                        }
                    }
                }
            }

            ui.divider()

            ui.relaxed.list {

                // ── Description ───────────────────────────────────────────────────
                if (description != null) {
                    noui.item {
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

                // ── Parameters ───────────────────────────────────────────────────
                val params = sortedVariants
                    .filterIsInstance<KlangCallable>()
                    .flatMap { it.params }
                    .distinctBy { it.name }

                if (params.isNotEmpty()) {
                    noui.item {
                        sectionLabel("Parameters")
                        noui.content {
                            ui.very.basic.compact.small.table Table {
                                css {
                                    color = Color(laf.textPrimary)
                                }
                                tbody {
                                    params.forEach { param ->
                                        tr {
                                            td {
                                                css { fontFamily = "monospace" }
                                                b { +param.name }
                                            }
                                            td {
                                                css { fontFamily = "monospace" }
                                                +"${param.type}"
                                            }
                                            td {
                                                +param.description
                                            }
                                        }
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
