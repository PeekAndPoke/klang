package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import kotlinx.html.Tag
import kotlinx.html.a

@Suppress("FunctionName")
fun Tag.CreditsPage() = comp {
    CreditsPage(it)
}

class CreditsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "credits-page"

            ui.basic.segment {

                ui.header H1 { +"Credits" }

                ui.segment {
                    ui.header H2 { +"Shout out to Strudel" }

                    ui.divider()

                    MarkdownDisplay(
                        """
                        **Klang exists because of Strudel.** What started as pure curiosity — *"how does this work?"* —
                        turned into a deep dive into the world of audio engines, pattern languages, and music synthesis.

                        Strudel's open-source code, its open sample libraries, and the excellent documentation at
                        [strudel.cc](https://strudel.cc) were invaluable in understanding the machinery behind it all.

                        What started as a copycat of Strudel is now evolving into its sibling — **Sprudel**.
                        Sharing the same roots and many of the same ideas, Sprudel takes the pattern language
                        in new directions as part of Klang.

                        A heartfelt thank you to the Strudel community for making all of this openly available.
                    """.trimIndent()
                    )

                    ui.divider()

                    ui.list {
                        noui.item {
                            a(href = "https://strudel.cc", target = "_blank") { +"https://strudel.cc" }
                        }
                        noui.item {
                            a(href = "https://codeberg.org/uzu/strudel", target = "_blank") { +"Strudel on Codeberg" }
                        }
                    }
                }
            }

        }
    }
}
