/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangMenuButton
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.klang.workshop.streams.smooth
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.combinedWith
import io.peekandpoke.ultra.streams.ops.filterIsInstance
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
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
) = comp(
    PlayerMiniStats.Props(showVolume = showVolume, menu = menu)
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

        /** How slowly the needles follow new readings: the share of the way kept per 10 ms, the Motor's old pace. */
        const val NEEDLE_SMOOTHING = 0.933

        /** Where a needle counts as there: a thousandth of its gauge's sweep, times the sweep below. */
        const val NEEDLE_NEAR = 0.001
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The master volume knob left of the gauges (KLANG-00330): the Motor shows it, the start page does not. */
        val showVolume: Boolean,
        /** The menu button in the spot beside the gauges (KLANG-00340); null keeps the spot empty. */
        val menu: KlangWorkbench.Menu?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The current player's latest diagnostics; null while there is no player, so the gauges go offline. */
    private val online = Player.player.switchMapNotNull { it.signals }
        .filterIsInstance<KlangPlaybackSignal.Diagnostics, _>()
        .combinedWith(Player.player) { signal, player -> signal?.diagnostics?.takeIf { player != null } }

    // The readings, smoothed as streams (KLANG-00345): the gauges draw what they get

    private val cylinders by subscribingTo(
        online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = 32 * NEEDLE_NEAR)
    )

    /** The heat is what the render does not have to spare. */
    private val heat by subscribingTo(
        online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = 1 * NEEDLE_NEAR)
    )

    private val voices by subscribingTo(
        online.map { d -> d?.activeVoiceCount?.toDouble() }
            .smooth(sharePer10Ms = NEEDLE_SMOOTHING, near = 100 * NEEDLE_NEAR)
    )

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
                        display = { "${(it * 100).roundToInt()}%" },
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
                                onClick = menu.toggle,
                                size = VOLUME_KNOB_PX.px,
                                key = KlangKeyBindings.MotorMenu,
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
