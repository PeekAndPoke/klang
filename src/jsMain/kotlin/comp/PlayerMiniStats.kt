package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
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
            playerDiagnosticsSubscription = player.signals.on<KlangPlaybackSignal.Diagnostics> { signal ->
                playerDiagnostics = signal
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
        playerDiagnostics?.let { signal ->
            div {
                ui.horizontal.list {
                    noui.bottom.aligned.item {
                        val active = signal.diagnostics.orbits.count { it.active }
                        roundOrbitsGauge(value = active.toDouble(), size = 50.px)
                    }

                    noui.bottom.aligned.item {
                        renderHeadroomGauge(value = signal.diagnostics.renderHeadroom, size = 62.px)
                    }

                    noui.bottom.aligned.item {
                        activeVoicesGauge(value = signal.diagnostics.activeVoiceCount, size = 50.px)
                    }
                }
            }
        }
    }
}
