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
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.filterIsInstance
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.css.Display
import kotlinx.css.display
import kotlinx.css.height
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
        const val SIDE_GAUGE_PX = 50.0

        /** The volume knob */
        const val VOLUME_KNOB_PX = 31.0

        /** Where the heat gauge turns hot, as in the kitchen sink (KLANG-00344). */
        const val HEAT_HOT_FROM = 0.85

        /** How slowly the needles follow new readings, as the Motor's gauges always did. */
        const val NEEDLE_SMOOTHING = 0.933
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The master volume knob left of the gauges (KLANG-00330): the Motor shows it, the start page does not. */
        val showVolume: Boolean,
        /** The menu button in the spot beside the gauges (KLANG-00340); null keeps the spot empty. */
        val menu: KlangWorkbench.Menu?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** Stream-subscribed — redraws the moment the player becomes ready. */
    private val currentPlayer by subscribingTo(Player.player)

    /** The current player's latest diagnostics; kept while the player is gone, until the next one has its own. */
    private val playerDiagnostics by subscribingTo(
        Player.player.switchMapNotNull { it.signals }
            .filterIsInstance<KlangPlaybackSignal.Diagnostics, _>()
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // Offline until the player is ready: the gauges show no reading then, and no glow (their tint is the theme's
        // `--kl-gauge-glow`; the start page sets its own)
        val diagnostics = playerDiagnostics?.diagnostics?.takeIf { currentPlayer != null }

        div {
            ui.horizontal.list {
                if (props.showVolume) {
                    noui.bottom.aligned.item {
                        VolumeKnob(size = VOLUME_KNOB_PX.px)
                    }
                }

                noui.bottom.aligned.item {
                    KlangGauge(
                        name = "Active cylinders",
                        value = diagnostics?.cylinders?.count { it.active }?.toDouble(),
                        range = 0.0..32.0,
                        size = SIDE_GAUGE_PX.px,
                        icon = { layer_group },
                        display = { it.roundToInt().toString() },
                        smoothing = NEEDLE_SMOOTHING,
                    )
                }

                noui.bottom.aligned.item {
                    // The heat is what the render does not have to spare
                    KlangGauge(
                        name = "Motor heat",
                        value = diagnostics?.renderHeadroom?.let { 1.0 - it },
                        size = MIDDLE_GAUGE_PX.px,
                        icon = { microchip },
                        display = { "${(it * 100).roundToInt()}%" },
                        hotFrom = HEAT_HOT_FROM,
                        smoothing = NEEDLE_SMOOTHING,
                    )
                }

                noui.bottom.aligned.item {
                    KlangGauge(
                        name = "Active voices",
                        value = diagnostics?.activeVoiceCount?.toDouble(),
                        range = 0.0..100.0,
                        size = SIDE_GAUGE_PX.px,
                        icon = { music },
                        display = { it.roundToInt().toString() },
                        smoothing = NEEDLE_SMOOTHING,
                    )
                }

                // The knob's size on the other side, so the middle gauge stays in the middle: the menu button when there
                // is one (KLANG-00340), else an empty place
                if (props.showVolume) {
                    noui.bottom.aligned.item {
                        val menu = props.menu

                        if (menu != null) {
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
