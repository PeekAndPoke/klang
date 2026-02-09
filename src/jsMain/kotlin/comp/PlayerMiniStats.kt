package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.semanticui.*
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.PlayerMiniStats() = comp {
    PlayerMiniStats(it)
}

class PlayerMiniStats(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playerDiagnosticsSubscription: (() -> Unit)? = null
    private var playerDiagnostics: KlangPlaybackSignal.Diagnostics? by value(null)

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await().also { player ->
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
                    getPlayer()
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
                        .filter { it.active }
                        .forEach { orbit ->
                            noui.item {
                                ui.labelSize().basic.inverted.white.label {
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
                                    room > 0.75 -> white
                                    room > 0.5 -> yellow
                                    room > 0.25 -> orange
                                    else -> green
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
