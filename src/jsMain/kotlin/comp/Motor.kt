/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import kotlinx.css.Color
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.bottom
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.paddingLeft
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.px
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
    paused: Stream<Boolean> = UiWork.resting,
) = comp(
    Motor.Props(menu = menu, paused = paused)
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
        /** The frame's total height: the gauges, the scope under them, the spectrum behind it down to the floor. */
        const val FRAME_HEIGHT_REM = 13.75

        /** Room above the gauges, so they do not crowd the divider above (the maintainer, 2026-10-27: "a bit larger"). */
        val TOP_INSET = 1.25.rem

        /** The top slot: the three gauges and the volume knob (62 px + their glow). */
        val TOP_SLOT_HEIGHT = 4.5.rem

        /**
         * The scope, just under the gauges, above the spectrum's lower part (the maintainer, 2026-10-26); its resting
         * line as far below the gauges as the divider is above them, 21px each, measured (the maintainer, 2026-10-27:
         * "the spacing between divider <-> gauges <-> osci is the same").
         */
        const val OSCILLOSCOPE_TOP_REM = 5.800
        const val OSCILLOSCOPE_HEIGHT_REM = 4.00

        /** How far the scope's smoke may go past it: up over the gauges, a little to the sides and down. */
        private val SMOKE = Oscilloscope.Smoke(top = 80.0, sides = 12.0, bottom = 12.0)

        /** The spectrum stands on the frame's floor, behind the scope; its boxes stack up from there. */
        const val SPECTRUM_HEIGHT_REM = 7.0

        /** The spectrum's columns, as on the design canvas (KLANG-00334 item 36; 16 before). */
        const val SPECTRUM_COLUMNS = 28

        /**
         * Each colour of the spectrum takes this share of a column, from the floor, and the red goes on to the top: a
         * song's columns seldom rise past the middle (the maintainer, 2026-10-26: "We always see blue and green, rarely
         * yellow and never red"; "take 15% steps please").
         */
        const val SPECTRUM_COLOR_STEP = 0.15

        /** The cells in a spectrum column, floor to top. */
        const val SPECTRUM_BOXES = 31

        /** The spectrum's colours, from the look, quiet to peak; the look editor's card shows them as here. */
        val SPECTRUM_ROLES = listOf(
            KlangRole.SpectrumQuiet,
            KlangRole.SpectrumLow,
            KlangRole.SpectrumMid,
            KlangRole.SpectrumHot,
            KlangRole.SpectrumPeak,
        )
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The menu button beside the gauges, opening a workbench's drawer; null for none (an empty spot). */
        val menu: KlangWorkbench.Menu?,
        /** While true, its gauges, scope and spectrum rest: a visual covers the page ([UiWork.resting], the default). */
        val paused: Stream<Boolean>,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The resolved look: the trace's colour is a role, read here for the canvas. */
    private val look by subscribingTo(KlangTheme.look)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "motor"

            css {
                textAlign = TextAlign.center
                position = Position.relative
                height = FRAME_HEIGHT_REM.rem
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
                PlayerMiniStats(showVolume = true, menu = props.menu, paused = props.paused)
            }

            div {
                key = "oscilloscope"
                css {
                    position = Position.absolute
                    top = OSCILLOSCOPE_TOP_REM.rem
                    left = 0.rem
                    right = 0.rem
                    height = OSCILLOSCOPE_HEIGHT_REM.rem
                    zIndex = 2
                }
                // The trace in the look's own colour for it (KLANG-00334 item 36): a new look changes it at once. It
                // radiates by itself (KLANG-00392), no glow behind it any more
                div {
                    css {
                        position = Position.relative
                        height = 100.pct
                        paddingLeft = 2.px
                    }

                    // Its smoke rises over the gauges (the maintainer, 2026-10-30: "would be nice if the smoke would
                    // cover the gauges")
                    Oscilloscope(
                        player = Player.player,
                        strokeColor = Color(look[KlangRole.ScopeTrace]),
                        paused = props.paused,
                        strokeWidth = 2.8,
                        swingPx = 22.0,
                        smoke = SMOKE,
                    )
                }
            }

            div {
                key = "spectrum-visualizer"

                css {
                    zIndex = 1
                    position = Position.absolute
                    // On the frame's floor, behind the scope
                    bottom = 0.rem
                    left = 0.rem
                    right = 0.rem
                    height = SPECTRUM_HEIGHT_REM.rem
                    width = 100.pct
                }

                // The hovered column's frequencies show in the middle of the spectrum's part under the scope (higher up the
                // trace would cover them)
                Spectrumeter(
                    // The look's spectrum section, quiet to peak (the maintainer, 2026-10-28)
                    colors = SPECTRUM_ROLES.map { Color(look[it]) },
                    numBoxesInStack = SPECTRUM_BOXES,
                    colorStep = SPECTRUM_COLOR_STEP,
                    numBuckets = SPECTRUM_COLUMNS,
                    labelMiddle = ((FRAME_HEIGHT_REM - OSCILLOSCOPE_TOP_REM - OSCILLOSCOPE_HEIGHT_REM) / 2.0).rem,
                    paused = props.paused,
                ) { Player.get() }
            }
        }
    }
}
