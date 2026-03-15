package io.peekandpoke.klang.pages.docs

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.comp.InViewport
import io.peekandpoke.klang.comp.KlangScriptReplComp
import io.peekandpoke.klang.script.docs.klangScriptDocSections
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.p

@Suppress("FunctionName")
fun Tag.KlangScriptDocsPage() = comp {
    KlangScriptDocsPage(it)
}

class KlangScriptDocsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

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
                    +"Interactive examples — edit the code and press Run to see the output."
                }
            }

            for (section in klangScriptDocSections) {
                div {
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

                    for (example in section.examples) {
                        val exampleTitle = example.title
                        if (exampleTitle != null) {
                            ui.sub.header {
                                css { put("color", "${laf.textSecondary} !important") }
                                +exampleTitle
                            }
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
    }
}
