package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.Motoer() = comp {
    Motoer(it)
}

class Motoer(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "motör"

            css {
                textAlign = TextAlign.center
                position = Position.relative
                // Flex column ensures the container height includes children's margins
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            div {
                key = "stats"
                css {
                    marginBottom = 6.px
                }
                PlayerMiniStats()
            }

            div {
                key = "oscilloscope"
                css { height = 60.px }
                Oscilloscope { Player.get() }
            }

            div {
                key = "spectrum-visualizer"

                val spectHeight = 150
                css {
                    zIndex = 1
                    position = Position.absolute
                    pointerEvents = PointerEvents.none
                    // Anchor to bottom
                    bottom = 0.px
                    left = 0.px
                    right = 0.px
                    // Dimensions
                    height = spectHeight.px
                    width = 100.pct

                    opacity = 0.5
                }

                Spectrumeter { Player.get() }
            }

            div {
                key = "title"

                css {
                    zIndex = 100
                    marginBottom = 16.px
                    opacity = 0.95
                }

                div {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                    }

                    onClick { router.navToUri(Nav.tour()) }

                    icon.music { css { marginRight = 10.px } }

                    ui.big.text {
                        css {
                            fontFamily = "monospace"
                            lineHeight = LineHeight("2.0em")
                            color = Color.white
                            display = Display.inlineBlock
                            fontWeight = FontWeight.bold
                        }
                        +"KLANG AUDIO MOTÖR"
                    }

                    icon.music { css { transform { scaleX(-1.0) }; marginLeft = 10.px } }
                }
            }
        }
    }
}
