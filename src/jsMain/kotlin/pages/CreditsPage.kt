package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
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
