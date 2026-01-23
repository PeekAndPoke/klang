package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Nav
import kotlinx.css.*
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.TourPage() = comp {
    TourPage(it)
}

class TourPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            ui.inverted.attached.segment {
                css {
                    minHeight = 100.vh
                    backgroundColor = Color.black
                    borderWidth = 0.px
                }

                ui.header H1 { +"Klang Audio Motör - Tour" }

                ui.inverted.cards {
                    ui.card {
                        onClick {
                            router.navToUri(Nav.dashboard())
                        }

                        noui.content {
                            icon.code()
                            +"Live Coding"
                        }
                    }
                }
            }
        }
    }
}
