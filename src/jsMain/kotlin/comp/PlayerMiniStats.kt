package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.semanticui.*
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
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
        val labelSize = semantic { mini }

        playerDiagnostics?.let { signal ->
            div {
                ui.horizontal.list {
                    signal.diagnostics.orbits
//                        .filter { it.active }
                        .forEach { orbit ->
                            noui.item {
                                ui.labelSize()
                                    .given(orbit.active) { basic.inverted.white }
                                    .givenNot(orbit.active) { basic.inverted.black }
                                    .label {
                                        title = "Active orbit #${orbit.id}"
                                        icon.satellite()
                                        +"${orbit.id}"
                                    }
                            }
                        }

                    noui.item {
                        ui.labelSize().basic.inverted.white.label {
                            title = "Audio render headroom"
                            val room = signal.diagnostics.renderHeadroom

                            val color = semanticIcon {
                                when {
                                    room > 0.500 -> white
                                    room > 0.333 -> yellow
                                    room > 0.166 -> orange
                                    else -> red
                                }
                            }

                            icon.color().microchip()

                            +signal.diagnostics.renderHeadroom.toFixed(2)
                        }
                    }

                    noui.item {
                        ui.labelSize().basic.inverted.white.label {
                            title = "Currently active voices"
                            icon.volume_up()
                            +"${signal.diagnostics.activeVoiceCount}"
                        }
                    }
                }
            }
        }
    }
}
