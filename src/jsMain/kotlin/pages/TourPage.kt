package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
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
