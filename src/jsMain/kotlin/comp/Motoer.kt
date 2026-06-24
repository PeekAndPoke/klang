/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.version
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.FontWeight
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.WhiteSpace
import kotlinx.css.bottom
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.fontFamily
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.lineHeight
import kotlinx.css.marginBottom
import kotlinx.css.marginLeft
import kotlinx.css.marginRight
import kotlinx.css.opacity
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.css.px
import kotlinx.css.right
import kotlinx.css.textAlign
import kotlinx.css.whiteSpace
import kotlinx.css.width
import kotlinx.css.zIndex
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.Motoer() = comp {
    Motoer(it)
}

class Motoer(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    // Build metadata, published as a stream; redraws once it loads so the title tooltip is current.
    private val versionInfo by subscribingTo(version)

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
                Oscilloscope(player = Player.player)
            }

            div {
                key = "spectrum-visualizer"

                val spectHeight = 132
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

                    opacity = 0.7
                }

                Spectrumeter(numBoxesInStack = 35) { Player.get() }
            }

            div {
                key = "title"

                css {
                    zIndex = 100
                    marginBottom = 8.px
                    opacity = 0.95
                }

                div {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        put("text-shadow", "0 0 5px #000")
                    }

                    // Reveal the build version as a native tooltip on hover
                    versionInfo.takeIf { it.isAvailable }?.let { info ->
                        title = buildString {
                            append(info.project).append(" v").append(info.version)
                            append("\nbranch: ").append(info.gitBranch)
                            append("\nrev: ").append(info.gitDesc)
                            info.date?.let { append("\nbuilt: ").append(it) }
                        }
                    }

                    onClick { router.navToUri(Nav.start()) }

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
