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
import kotlinx.css.properties.Transform
import kotlinx.css.properties.deg
import kotlinx.css.rem
import kotlinx.css.transform
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

            ui.four.stackable.link.cards {

                ui.horizontal.card {
                    onClick { router.navToUri(Nav.manualsSprudel()) }

                    noui.image {
                        ui.basic.segment {
                            icon.big.wind {
                                css {
                                    transform += Transform("rotate", arrayOf((-90).deg))
                                }
                            }
                        }
                    }
                    noui.middle.aligned.content {
                        ui.large.header { +"Sprudel" }
                    }
                }

                ui.horizontal.card {
                    onClick { router.navToUri(Nav.manualsKlangScript()) }

                    noui.image {
                        ui.basic.segment {
                            icon.big.code()
                        }
                    }
                    noui.middle.aligned.content {
                        ui.large.header { +"KlangScript" }
                    }
                }

                ui.horizontal.card {
                    onClick { router.navToUri(Nav.tutorials()) }

                    noui.image {
                        ui.basic.segment {
                            icon.big.graduation_cap()
                        }
                    }
                    noui.middle.aligned.content {
                        ui.large.header { +"Tutorials" }
                    }
                }
            }
        }
    }
}
