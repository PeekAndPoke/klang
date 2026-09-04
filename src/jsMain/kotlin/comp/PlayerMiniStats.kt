/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.css.Color
import kotlinx.css.WhiteSpace
import kotlinx.css.color
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.marginTop
import kotlinx.css.px
import kotlinx.css.whiteSpace
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
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
        // "Motor online" = the player is ready — the gauges shine a faint glow.
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

            // The warehouse in one line: what the backend holds, and the two counters a listener
            // can hear (a dropped voice was late, a denied rent is a delay or room refused for
            // lack of memory). The snapshot is maintained on the backend and only re-sent when
            // something changed, so this is a plain read of the last Diagnostics.
            playerDiagnostics?.diagnostics?.warehouse?.let { w ->
                div {
                    css {
                        fontSize = 10.px
                        color = Color(KlangTheme.Hex.textTertiary)
                        fontFamily = "monospace"
                        marginTop = 2.px
                        whiteSpace = WhiteSpace.nowrap
                    }
                    title = warehouseTooltip(w)
                    +warehouseLine(w)
                }
            }
        }
    }

    /** `shelf 6.2M · smp 12.3M · idle 16/16/8 · dry 0 · late 0`: bytes idle on the ring shelf, sample PCM, idle units, refusals, late voices. */
    private fun warehouseLine(w: KlangCommLink.Feedback.Diagnostics.WarehouseStats): String {
        val problems = if (w.deniedRents > 0 || w.droppedVoices > 0) " · dry ${w.deniedRents} · late ${w.droppedVoices}" else ""
        return "shelf ${mb(w.ringIdleBytes)} · smp ${mb(w.sampleBytes)} · idle ${w.ringIdleCount}/${w.reverbIdleCount}/${w.cylinderIdleCount}$problems"
    }

    private fun warehouseTooltip(w: KlangCommLink.Feedback.Diagnostics.WarehouseStats): String = listOf(
        "Resource warehouse",
        "rings: ${mb(w.ringIdleBytes)} idle (${w.ringIdleCount}, ${w.ringDirtyCount} dirty) · allocated ${w.ringAllocations} · hits ${w.ringHits} · failed ${w.ringFailures} · dropped ${w.ringDropped} · sync cleans ${w.ringSyncCleans}",
        "reverbs: ${w.reverbIdleCount} idle (${w.reverbDirtyCount} dirty) · allocated ${w.reverbAllocations} · hits ${w.reverbHits} · failed ${w.reverbFailures} · dropped ${w.reverbDropped}",
        "cylinders: ${w.cylinderIdleCount} idle · allocated ${w.cylinderAllocations} · hits ${w.cylinderHits} · dropped ${w.cylinderDropped}",
        "scratch: capacity ${w.scratchCapacity} · high water ${w.scratchHighWater} · late allocations ${w.scratchLateAllocations} · unbalanced releases ${w.scratchUnbalancedReleases}",
        "samples: ${mb(w.sampleBytes)} in ${w.sampleCount} · allocation failures ${w.sampleAllocationFailures}",
        "voices dropped as late: ${w.droppedVoices} · delay/room rents refused: ${w.deniedRents}",
    ).joinToString("\n")

    private fun mb(bytes: Double): String = "${(bytes / (1024.0 * 1024.0)).toFixed(1)}M"
}
