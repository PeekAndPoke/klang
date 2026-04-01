package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.ticker
import kotlinx.css.px
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.PlayerMiniStats() = comp {
    PlayerMiniStats(it)
}

class PlayerMiniStats(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playerDiagnosticsSubscription: (() -> Unit)? = null
    private var playerDiagnostics: KlangPlaybackSignal.Diagnostics? = null

    @Suppress("unused")
    private val ticker by subscribingTo(ticker(16.milliseconds)) { update() }
    private var _player: KlangPlayer? = null

    private fun update() {
        _player?.let { return }

        _player ?: Player.get()?.also { player ->
            // Unsubscribe
            playerDiagnosticsSubscription?.invoke()
            // Resubscribe
            playerDiagnosticsSubscription = player.signals.subscribeToStream { signal ->
                if (signal is KlangPlaybackSignal.Diagnostics) {
                    playerDiagnostics = signal
                }
            }
        }
    }

    init {
        lifecycle {
            onMount {
                launch {
                    update()
                }
            }

            onUnmount {
                playerDiagnosticsSubscription?.invoke()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            ui.horizontal.list {
                noui.bottom.aligned.item {
                    val active = playerDiagnostics?.diagnostics?.cylinders?.count { it.active }
                    roundCylindersGauge(value = active?.toDouble(), size = 50.px)
                }

                noui.bottom.aligned.item {
                    val headroom = playerDiagnostics?.diagnostics?.renderHeadroom
                    renderMotorHeatGauge(value = headroom, size = 62.px)
                }

                noui.bottom.aligned.item {
                    val voiceCount = playerDiagnostics?.diagnostics?.activeVoiceCount
                    activeVoicesGauge(value = voiceCount, size = 50.px)
                }
            }
        }
    }
}
