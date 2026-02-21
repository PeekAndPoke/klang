package io.peekandpoke.klang.pages.docs

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
import kotlinx.css.Padding
import kotlinx.css.padding
import kotlinx.css.rem
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.DocsPage() = comp {
    DocsPage(it)
}

class DocsPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css {
                padding = Padding(2.rem)
            }

            ui.segment {
                ui.header { +"Motör Docs" }
            }

            ui.two.stackable.link.cards {

                noui.card {
                    onClick { router.navToUri(Nav.docsStrudel()) }

                    noui.content {
                        ui.header { +"Strudel" }
                    }
                    noui.content {
                        icon.large.wind()
                    }
                }

                noui.card {
                    onClick { router.navToUri(Nav.docsKlangScript()) }

                    noui.content {
                        ui.header { +"KlangScript" }
                    }
                    noui.content {
                        icon.large.code()
                    }
                }
            }
        }
    }
}
