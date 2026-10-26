/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.bottom
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.opacity
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.rem
import kotlinx.css.right
import kotlinx.css.textAlign
import kotlinx.css.top
import kotlinx.css.width
import kotlinx.css.zIndex
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.Motoer(
    menu: KlangWorkbench.Menu? = null,
) = comp(
    Motor.Props(menu = menu)
) {
    Motor(it)
}

/**
 * The Motor: the gauges, the oscilloscope, and the spectrum behind them, in the workbench's corner (KLANG-00341). Its
 * title is written up the workbench's tool strip; its warehouse stats and the build live in the Motor tool window.
 *
 * Every slot is positioned ABSOLUTELY in rem inside a fixed-height frame; the geometry is in the companion.
 */
class Motor(ctx: Ctx<Props>) : Component<Motor.Props>(ctx) {

    companion object {
        /** The frame's total height: the inset, the scope's foot (8.75 rem) and a floor under it for the spectrum. */
        val FRAME_HEIGHT = 11.5.rem

        /** Room above the gauges, so they do not touch the column's edge. */
        val TOP_INSET = 0.75.rem

        /** The top slot: the three gauges and the volume knob (62 px + their glow). */
        val TOP_SLOT_HEIGHT = 4.5.rem

        /** Where the oscilloscope starts under the inset and how tall it is. */
        val OSCILLOSCOPE_TOP = 5.0.rem
        val OSCILLOSCOPE_HEIGHT = 3.75.rem
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The menu button beside the gauges, opening a workbench's drawer; null for none (an empty spot). */
        val menu: KlangWorkbench.Menu?,
    )

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
                key = "stats"
                css {
                    position = Position.absolute
                    top = TOP_INSET
                    left = 0.rem
                    right = 0.rem
                    height = TOP_SLOT_HEIGHT
                    zIndex = 2
                }
                PlayerMiniStats(showVolume = true, menu = props.menu)
            }

            div {
                key = "oscilloscope"
                css {
                    position = Position.absolute
                    top = OSCILLOSCOPE_TOP + TOP_INSET
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
                    // From the frame's floor up to the scope's top, behind it
                    bottom = 0.rem
                    left = 0.rem
                    right = 0.rem
                    height = FRAME_HEIGHT - TOP_INSET - OSCILLOSCOPE_TOP
                    width = 100.pct

                    opacity = 0.66
                }

                Spectrumeter(numBoxesInStack = 35) { Player.get() }
            }
        }
    }
}
