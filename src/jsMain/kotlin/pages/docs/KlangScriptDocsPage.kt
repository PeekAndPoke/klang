package io.peekandpoke.klang.pages.docs

import io.peekandpoke.klang.comp.InViewport
import io.peekandpoke.klang.comp.KlangScriptReplComp
import io.peekandpoke.klang.script.docs.DocSection
import io.peekandpoke.klang.script.docs.JsCompat
import io.peekandpoke.klang.script.docs.klangScriptDocSections
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticTag
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.Margin
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.fontSize
import kotlinx.css.gap
import kotlinx.css.margin
import kotlinx.css.marginBottom
import kotlinx.css.marginRight
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangScriptDocsPage() = comp {
    KlangScriptDocsPage(it)
}

class KlangScriptDocsPage(ctx: NoProps) : PureComponent(ctx) {

    //  TYPES  //////////////////////////////////////////////////////////////////////////////////////////////////

    enum class Filter(val label: String) {
        All("All examples"),
        CompatibleOnly("JS compatible only"),
        IncompatibleOnly("JS incompatible only");

        fun matches(example: io.peekandpoke.klang.script.docs.DocExample): Boolean = when (this) {
            All -> true
            CompatibleOnly -> example.jsCompat == JsCompat.Compatible
            IncompatibleOnly -> example.jsCompat == JsCompat.Incompatible
        }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private var filter by value(Filter.All)

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            ui.segment {
                ui.huge.header {
                    css { put("color", "${laf.textPrimary} !important") }
                    +"KlangScript Language Reference"
                }
                p {
                    css { color = Color(laf.textSecondary) }
                    +"Welcome to KlangScript! It's the dedicated scripting language for the Klang Audio Motór. "
                    +"While it feels a lot like JavaScript in many ways, it has its own special flavor and unique "
                    +"differences designed specifically for making music."
                }
                p {
                    css { color = Color(laf.textSecondary) }
                    +"Interactive examples — edit the code and press Run to see the output. "
                    +"Each example is tagged "
                    jsCompatLabel(JsCompat.Compatible)
                    +" if it is valid JavaScript as-is, or "
                    jsCompatLabel(JsCompat.Incompatible)
                    +" if it uses KlangScript-only features."
                }

                renderFilterBar()
            }

            val visibleSections = klangScriptDocSections
                .map { it to it.examples.filter(filter::matches) }
                .filter { (_, examples) -> examples.isNotEmpty() }

            if (visibleSections.isEmpty()) {
                ui.segment {
                    p {
                        css { color = Color(laf.textSecondary) }
                        +"No examples match the current filter."
                    }
                }
            }

            for ((section, examples) in visibleSections) {
                renderSection(section, examples)
            }
        }
    }

    private fun FlowContent.renderFilterBar() {
        div {
            css {
                marginTop = 0.75.rem
                display = Display.flex
                alignItems = Align.center
                gap = 0.5.rem
                flexWrap = FlexWrap.wrap
            }

            span {
                css {
                    color = Color(laf.textSecondary)
                    fontSize = 0.9.rem
                    marginRight = 0.25.rem
                }
                +"Filter:"
            }

            for (option in Filter.values()) {
                filterButton(option)
            }
        }
    }

    private fun FlowContent.filterButton(option: Filter) {
        val active = filter == option
        val base: SemanticTag = if (active) ui.tiny.grey.button else ui.tiny.basic.button
        base {
            onClick { filter = option }
            when (option) {
                Filter.All -> {}
                Filter.CompatibleOnly -> icon.check {
                    css { color = Color(laf.excellent) }
                }

                Filter.IncompatibleOnly -> icon.exclamation_triangle {
                    css { color = Color(laf.warning) }
                }
            }
            +option.label
        }
    }

    private fun FlowContent.renderSection(
        section: DocSection,
        examples: List<io.peekandpoke.klang.script.docs.DocExample>,
    ) {
        div {
            key = "section::${section.title}"

            css {
                marginBottom = 2.rem
            }

            ui.dividing.header {
                css { put("color", "${laf.textPrimary} !important") }
                +section.title
            }

            p {
                css { color = Color(laf.textSecondary) }
                +section.description
            }

            examples.forEachIndexed { index, example ->
                div {
                    key = "example::${section.title}::${example.title ?: "untitled-$index"}"

                    if (index > 0) {
                        ui.divider()
                    }

                    div {
                        css {
                            display = Display.flex
                            alignItems = Align.center
                            gap = 0.5.rem
                            marginTop = 0.75.rem
                        }

                        val exampleTitle = example.title
                        if (exampleTitle != null) {
                            ui.sub.header {
                                css {
                                    put("color", "${laf.textSecondary} !important")
                                    margin = Margin(0.px)
                                }
                                +exampleTitle
                            }
                        }

                        jsCompatLabel(example.jsCompat)
                    }

                    val exampleDescription = example.description
                    if (exampleDescription != null) {
                        p {
                            css {
                                color = Color(laf.textSecondary)
                                fontSize = 0.9.rem
                                marginTop = 0.25.rem
                                marginBottom = 0.25.rem
                            }
                            +exampleDescription
                        }
                    }

                    InViewport {
                        KlangScriptReplComp(initialCode = example.code)
                    }
                }
            }
        }
    }

    private fun FlowContent.jsCompatLabel(compat: JsCompat) {
        ui.tiny.basic.label {
            when (compat) {
                JsCompat.Compatible -> {
                    icon.check { css { color = Color(laf.excellent) } }
                    +"JS compatible"
                }

                JsCompat.Incompatible -> {
                    icon.exclamation_triangle { css { color = Color(laf.warning) } }
                    +"JS INCOMPATIBLE"
                }
            }
        }
    }
}
