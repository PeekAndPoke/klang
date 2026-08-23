/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.css.Color
import kotlinx.css.px
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.PlayerMiniStats(
    glowColor: Color? = null,
    glowIntensity: Double = 0.45,
) = comp(
    PlayerMiniStats.Props(glowColor = glowColor, glowIntensity = glowIntensity)
) {
    PlayerMiniStats(it)
}

class PlayerMiniStats(ctx: Ctx<Props>) : Component<PlayerMiniStats.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** Tint of the gauge glow — null keeps the editor-frame accent. */
        val glowColor: Color?,
        /** Strength of the gauge glow, 0.0..1.0. */
        val glowIntensity: Double,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playerDiagnosticsSubscription: (() -> Unit)? = null
    private var playerDiagnostics: KlangPlaybackSignal.Diagnostics? = null

    @Suppress("unused")
    private val ticker by subscribingTo(ticker(16.milliseconds))

    /** Stream-subscribed — redraws the moment the player becomes ready. */
    private val currentPlayer by subscribingTo(Player.player) { p ->
        if (p != null) {
            // Unsubscribe
            playerDiagnosticsSubscription?.invoke()
            // Resubscribe
            playerDiagnosticsSubscription = p.signals.subscribeToStream { signal ->
                if (signal is KlangPlaybackSignal.Diagnostics) {
                    playerDiagnostics = signal
                }
            }

        }
    }

    init {
        lifecycle {
            onUnmount {
                playerDiagnosticsSubscription?.invoke()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        // "Motör online" = the player is ready — the gauges shine a faint glow.
        // Default tint is the muted accent, matching the editor frame; the start
        // page overrides it with the background light so one lamp lights the page.
        val glow = when {
            currentPlayer == null -> null
            else -> props.glowColor ?: Color(KlangTheme.Hex.accentMuted)
        }

        div {
            ui.horizontal.list {
                noui.bottom.aligned.item {
                    val active = playerDiagnostics?.diagnostics?.cylinders?.count { it.active }
                    roundCylindersGauge(
                        value = active?.toDouble(),
                        size = 50.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }

                noui.bottom.aligned.item {
                    val headroom = playerDiagnostics?.diagnostics?.renderHeadroom
                    renderMotorHeatGauge(
                        value = headroom,
                        size = 62.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }

                noui.bottom.aligned.item {
                    val voiceCount = playerDiagnostics?.diagnostics?.activeVoiceCount
                    activeVoicesGauge(
                        value = voiceCount,
                        size = 50.px,
                        glowColor = glow,
                        glowIntensity = props.glowIntensity,
                    )
                }
            }
        }
    }
}
