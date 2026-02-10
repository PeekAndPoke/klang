package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.css.Color
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
        val diag = playerDiagnostics

        playerDiagnostics?.let { signal ->
            div {
                ui.horizontal.list {
                    noui.bottom.aligned.item {
                        val active = signal.diagnostics.orbits.count { it.active }

                        Gauge(
                            size = 50.px,
                            value = active.toDouble(),
                            display = active.toString(),
                            title = "Active Orbits",
                            range = 0.0..10.0,
                            icon = { small.satellite },
                            iconColors = listOf(
                                0.0..4.0 to Color.lightGreen,
                                4.0..6.0 to Color.yellow,
                                6.0..8.0 to Color.orange,
                                8.0..Double.MAX_VALUE to Color.red,
                            )
                        )
                    }

                    noui.bottom.aligned.item {
                        Gauge(
                            size = 62.px,
                            value = 1.0 - signal.diagnostics.renderHeadroom,
                            display = signal.diagnostics.renderHeadroom.toFixed(2),
                            title = "Render Headroom",
                            range = 0.0..1.0,
                            icon = { small.microchip },
                            iconColors = listOf(
                                Double.MIN_VALUE..0.4 to Color.lightGreen,
                                0.2..0.6 to Color.yellow,
                                0.4..0.8 to Color.orange,
                                0.6..Double.MAX_VALUE to Color.red,
                            )
                        )
                    }

                    noui.bottom.aligned.item {
                        Gauge(
                            size = 50.px,
                            value = signal.diagnostics.activeVoiceCount.toDouble(),
                            display = signal.diagnostics.activeVoiceCount.toString().padStart(2, '0'),
                            title = "Active Voices",
                            range = 0.0..80.0,
                            icon = { small.volume_up },
                            iconColors = listOf(
                                0.0..30.0 to Color.lightGreen,
                                20.0..40.0 to Color.yellow,
                                30.0..50.0 to Color.orange,
                                50.0..Double.MAX_VALUE to Color.red,
                            )
                        )
                    }
                }
            }
        }
    }
}
