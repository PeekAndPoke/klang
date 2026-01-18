package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiTextArea
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.DashboardPage() = comp {
    DashboardPage(it)
}

class DashboardPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    var loading: Boolean by value(false)
    var player: KlangPlayer? by value(null)
    var song: KlangPlayback? by value(null)

    var input: String by value(
        """
            sound("bd hh sd oh")
        """.trimIndent()
    )

    init {
        lifecycle {
            onUnmount {
                player?.shutdown()
            }
        }
    }

    private suspend fun createPlayer(): KlangPlayer {
        if (loading) error("Already loading")

        loading = true

        val samples = Samples.create(catalogue = SampleCatalogue.default)

        val playerOptions = KlangPlayer.Options(
            samples = samples,
            sampleRate = 48000,
        )

        return klangPlayer(playerOptions).also {
            player = it
            loading = false
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {

        ui.container {
            ui.header H1 { +"Klang Audio Engine" }

            ui.form {

                ui.fields {
                    ui.button {
                        onClick {
                            song?.stop()

                            launch {
                                if (!loading) {
                                    createPlayer().let { p ->
                                        val compiled = StrudelPattern.compile(input)!!

                                        song = p.playStrudel(compiled)
                                        song?.start()
                                    }
                                }
                            }
                        }

                        if (loading) {
                            icon.loading.spinner()
                        } else {
                            icon.play()
                            +"Play"
                        }
                    }
                    ui.button {
                        onClick {
                            song?.stop()
                        }

                        icon.stop()
                        +"Stop"
                    }
                }

                UiTextArea(input, { input = it })
            }
        }
    }
}
