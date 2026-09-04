/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.version
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FontWeight
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.WhiteSpace
import kotlinx.css.bottom
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.em
import kotlinx.css.fontFamily
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.lineHeight
import kotlinx.css.opacity
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.properties.LineHeight
import kotlinx.css.rem
import kotlinx.css.top
import kotlinx.css.right
import kotlinx.css.cursor
import kotlinx.css.textAlign
import kotlinx.css.whiteSpace
import kotlinx.css.width
import kotlinx.css.zIndex
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.Motoer() = comp {
    Motor(it)
}

/**
 * The Motor: gauges (or, on a click on the title, the warehouse stats), the oscilloscope, the
 * spectrum behind everything, and the title.
 *
 * Every slot is positioned ABSOLUTELY in rem inside a fixed-height frame, so that whatever the top
 * slot shows, the oscilloscope keeps its distance to the bottom (it used to be pushed up by the
 * height of the text under the gauges). The geometry is in the companion; the top slot's height is
 * shared with `PlayerWarehouseStats`, which renders at exactly that height.
 */
class Motor(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The frame's total height. */
        val FRAME_HEIGHT = 13.0.rem

        /** The top slot: the three gauges (62 px + their glow) or the warehouse stats. */
        val TOP_SLOT_HEIGHT = 4.5.rem

        /** Where the oscilloscope starts and how tall it is — its bottom is fixed at 8.75 rem from the top. */
        val OSCILLOSCOPE_TOP = 5.0.rem
        val OSCILLOSCOPE_HEIGHT = 3.75.rem

        /** The title's box, anchored to the bottom. */
        val TITLE_HEIGHT = 2.6.rem
        val TITLE_BOTTOM = 0.5.rem

        /** The spectrum's height, anchored to the bottom, behind everything. */
        val SPECTRUM_HEIGHT = 8.25.rem
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    // Build metadata, published as a stream; redraws once it loads so the title tooltip is current.
    private val versionInfo by subscribingTo(version)

    /** A click on the title toggles the top slot between the gauges and the warehouse stats. */
    private var showWarehouse by value(false)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "motor"

            css {
                textAlign = TextAlign.center
                position = Position.relative
                height = FRAME_HEIGHT
            }

            div {
                key = if (showWarehouse) "warehouse" else "stats"
                css {
                    position = Position.absolute
                    top = 0.rem
                    left = 0.rem
                    right = 0.rem
                    height = TOP_SLOT_HEIGHT
                    zIndex = 2
                }
                if (showWarehouse) {
                    PlayerWarehouseStats()
                } else {
                    PlayerMiniStats()
                }
            }

            div {
                key = "oscilloscope"
                css {
                    position = Position.absolute
                    top = OSCILLOSCOPE_TOP
                    left = 0.rem
                    right = 0.rem
                    height = OSCILLOSCOPE_HEIGHT
                    zIndex = 2
                }
                Oscilloscope(player = Player.player)
            }

            div {
                key = "spectrum-visualizer"

                css {
                    zIndex = 1
                    position = Position.absolute
                    pointerEvents = PointerEvents.none
                    // Anchor to bottom
                    bottom = 0.rem
                    left = 0.rem
                    right = 0.rem
                    height = SPECTRUM_HEIGHT
                    width = 100.pct

                    opacity = 0.66
                }

                Spectrumeter(numBoxesInStack = 35) { Player.get() }
            }

            div {
                key = "title"

                css {
                    zIndex = 100
                    position = Position.absolute
                    bottom = TITLE_BOTTOM
                    left = 0.rem
                    right = 0.rem
                    height = TITLE_HEIGHT
                    opacity = 0.95
                }

                div {
                    css {
                        whiteSpace = WhiteSpace.nowrap
                        cursor = Cursor.pointer
                        put("text-shadow", "0 0 5px #000")
                    }

                    // Reveal the build version as a native tooltip on hover
                    versionInfo.takeIf { it.isAvailable }?.let { info ->
                        title = buildString {
                            append(info.project).append(" v").append(info.version)
                            append("\nbranch: ").append(info.gitBranch)
                            append("\nrev: ").append(info.gitDesc)
                            info.date?.let { append("\nbuilt: ").append(it) }
                            append("\n\nclick: ").append(if (showWarehouse) "back to the gauges" else "warehouse stats")
                        }
                    }

                    // The title toggles the top slot; the start page is one click away in the menu.
                    onClick { showWarehouse = !showWarehouse }

                    ui.big.text {
                        css {
                            height = 2.0.em
                            fontFamily = "monospace"
                            lineHeight = LineHeight("2.0em")
                            color = Color.white
                            display = Display.inlineBlock
                            fontWeight = FontWeight.bold
                        }
                        +"KLANGMOTOR"
                    }
                }
            }
        }
    }
}
