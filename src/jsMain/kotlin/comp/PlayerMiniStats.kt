/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.filterIsInstance
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.PlayerMiniStats(
    glowColor: Color? = null,
    glowIntensity: Double = 0.45,
    showVolume: Boolean = false,
) = comp(
    PlayerMiniStats.Props(glowColor = glowColor, glowIntensity = glowIntensity, showVolume = showVolume)
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
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** Tint of the gauge glow — null keeps the editor-frame accent. */
        val glowColor: Color?,
        /** Strength of the gauge glow, 0.0..1.0. */
        val glowIntensity: Double,
        /** The master volume knob left of the gauges (KLANG-00330): the Motor shows it, the start page does not. */
        val showVolume: Boolean,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    @Suppress("unused")
    private val ticker by subscribingTo(ticker(16.milliseconds))

    /** Stream-subscribed — redraws the moment the player becomes ready. */
    private val currentPlayer by subscribingTo(Player.player)

    /** The current player's latest diagnostics; kept while the player is gone, until the next one has its own. */
    private val playerDiagnostics by subscribingTo(
        Player.player.switchMapNotNull { it.signals }
            .filterIsInstance<KlangPlaybackSignal.Diagnostics, _>()
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // "Motor online" = the player is ready — the gauges shine a faint glow.
        // Default tint is the muted accent, matching the editor frame; the start
        // page overrides it with the background light so one lamp lights the page.
        val glow = when {
            currentPlayer == null -> null
            else -> props.glowColor ?: Color(KlangTheme.Hex.accentMuted)
        }

        div {
            ui.horizontal.list {
                if (props.showVolume) {
                    noui.bottom.aligned.item {
                        VolumeKnob(
                            size = VOLUME_KNOB_PX.px,
                            motorOnline = currentPlayer != null,
                            glowColor = glow,
                            glowIntensity = props.glowIntensity,
                        )
                    }
                }

                noui.bottom.aligned.item {
                    val active = playerDiagnostics?.diagnostics?.cylinders?.count { it.active }
                    roundCylindersGauge(
                        value = active?.toDouble(),
                        size = SIDE_GAUGE_PX.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }

                noui.bottom.aligned.item {
                    val headroom = playerDiagnostics?.diagnostics?.renderHeadroom
                    renderMotorHeatGauge(
                        value = headroom,
                        size = MIDDLE_GAUGE_PX.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }

                noui.bottom.aligned.item {
                    val voiceCount = playerDiagnostics?.diagnostics?.activeVoiceCount
                    activeVoicesGauge(
                        value = voiceCount,
                        size = SIDE_GAUGE_PX.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }

                // An empty place of the knob's size on the other side, so the middle gauge stays in the middle
                if (props.showVolume) {
                    noui.bottom.aligned.item {
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
