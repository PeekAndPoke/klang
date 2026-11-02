/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.spine.MotorReadings
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangMenuButton
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.combinedWith
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import kotlinx.css.Display
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.marginLeft
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.PlayerMiniStats(
    showVolume: Boolean = false,
    menu: KlangWorkbench.Menu? = null,
    paused: Stream<Boolean> = UiWork.resting,
) = comp(
    PlayerMiniStats.Props(showVolume = showVolume, menu = menu, paused = paused)
) {
    PlayerMiniStats(it)
}

class PlayerMiniStats(ctx: Ctx<Props>) : Component<PlayerMiniStats.Props>(ctx) {

    companion object {
        /** The middle gauge, the Motor heat. */
        const val MIDDLE_GAUGE_PX = 62.0

        /** The gauges beside it. */
        const val SIDE_GAUGE_PX = 52.0

        /** The volume knob, and the menu button's ring opposite it. */
        const val VOLUME_KNOB_PX = 42.0

        /**
         * How much closer the knob and the menu button sit to their gauges than the gauges to each other: beside a
         * smaller thing the same gap looks wider (the maintainer, 2026-10-26).
         */
        const val OUTER_GAP_TRIM_PX = 2.0

        /** Where the heat gauge turns hot, as in the kitchen sink (KLANG-00344). */
        const val HEAT_HOT_FROM = 0.85
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The master volume knob left of the gauges (KLANG-00330): the Motor shows it, the start page does not. */
        val showVolume: Boolean,
        /** The menu button in the spot beside the gauges (KLANG-00340); null keeps the spot empty. */
        val menu: KlangWorkbench.Menu?,
        /** While true, the readings are let go and the needles rest ([UiWork.resting], the default). */
        val paused: Stream<Boolean>,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * When the gauges let go of the shared readings: while paused, but not for the window resting alone. The readings
     * rest with the window themselves, upstream of their smoothing, so a needle held on to lands its swing and swings on
     * when the window comes back; let go of, it would freeze mid-swing and snap on its return.
     */
    private val letGo = props.paused.combinedWith(UiWork.resting) { paused, resting -> paused && !resting }

    // The readings, smoothed once in the Spine for every gauge (KLANG-00345, KLANG-00389 step 4): the gauges draw what
    // they get

    private val cylinders by subscribingTo(MotorReadings.cylinders.cutoffWhen(letGo))

    private val heat by subscribingTo(MotorReadings.heat.cutoffWhen(letGo))

    private val voices by subscribingTo(MotorReadings.voices.cutoffWhen(letGo))

    /** The Gui-dance's lit step: the menu button wears its beacon when it is the button's (KLANG-00350). */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // Offline until the player is ready: the gauges show no reading then, and no glow (their tint is the theme's
        // `--kl-gauge-glow`; the start page sets its own)
        div {
            ui.horizontal.list {
                if (props.showVolume) {
                    noui.bottom.aligned.item {
                        VolumeKnob(size = VOLUME_KNOB_PX.px)
                    }
                }

                noui.bottom.aligned.item {
                    // Fomantic spaces the items by their right margin; a negative left one takes from that gap
                    if (props.showVolume) {
                        css { marginLeft = (-OUTER_GAP_TRIM_PX).px }
                    }

                    KlangGauge(
                        name = "Active cylinders",
                        value = cylinders,
                        range = 0.0..32.0,
                        size = SIDE_GAUGE_PX.px,
                        icon = { layer_group },
                        display = { it.roundToInt().toString() },
                    )
                }

                noui.bottom.aligned.item {
                    KlangGauge(
                        name = "Motor heat",
                        value = heat,
                        size = MIDDLE_GAUGE_PX.px,
                        icon = { microchip },
                        display = { "${(it * 100).roundToInt()}" },
                        hotFrom = HEAT_HOT_FROM,
                    )
                }

                noui.bottom.aligned.item {
                    KlangGauge(
                        name = "Active voices",
                        value = voices,
                        range = 0.0..100.0,
                        size = SIDE_GAUGE_PX.px,
                        icon = { music },
                        display = { it.roundToInt().toString() },
                    )
                }

                // The knob's size on the other side, so the middle gauge stays in the middle: the menu button when there
                // is one (KLANG-00340), else an empty place
                if (props.showVolume) {
                    noui.bottom.aligned.item {
                        css { marginLeft = (-OUTER_GAP_TRIM_PX).px }

                        val menu = props.menu

                        if (menu != null) {
                            // Its ring the knob's size, so its button is the knob's cap's size (the maintainer, 2026-10-26)
                            KlangMenuButton(
                                open = menu.open,
                                onClick = {
                                    // Opening the drawer is the Gui-dance's step; closing it is not
                                    if (!menu.open) {
                                        KlangGuiDance.dance.used(KlangGuiDance.MOTOR_MENU)
                                    }

                                    menu.toggle()
                                },
                                size = VOLUME_KNOB_PX.px,
                                key = KlangKeyBindings.MotorMenu,
                                // Shift Shift, the search, opens the menu too (the maintainer, 2026-11-02)
                                alsoKey = KlangKeyBindings.SearchEverywhere,
                                beacon = guiDanceLit == KlangGuiDance.MOTOR_MENU,
                            )
                        } else {
                            div {
                                css {
                                    display = Display.inlineBlock
                                    width = VOLUME_KNOB_PX.px
                                    height = VOLUME_KNOB_PX.px
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
