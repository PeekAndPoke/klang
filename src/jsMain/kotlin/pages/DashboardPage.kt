package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.semanticui.forms.UiTextArea
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.ops.persistInLocalStorage
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.html.Tag
import kotlinx.serialization.builtins.serializer

@Suppress("FunctionName")
fun Tag.DashboardPage() = comp {
    DashboardPage(it)
}

class DashboardPage(ctx: NoProps) : PureComponent(ctx) {

    val inputStream = StreamSource(
        """
        sound("bd hh sd oh")
    """.trimIndent()
    ).persistInLocalStorage("current-song", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    var loading: Boolean by value(false)
    var player: KlangPlayer? by value(null)
    var song: StrudelPlayback? by value(null)

    val input: String by subscribingTo(inputStream)
    var cps: Double by value(0.5) {
        song?.updateCyclesPerSecond(it)
    }


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

                ui.six.fields.fields {
                    ui.field {
                        ui.fluid.button {
                            onClick {
                                when (val s = song) {
                                    null -> launch {
                                        if (!loading) {
                                            createPlayer().let { p ->
                                                val pattern = StrudelPattern.compile(input)!!

                                                song = p.playStrudel(pattern)
                                                song?.start(
                                                    StrudelPlayback.Options(cyclesPerSecond = cps)
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        val pattern = StrudelPattern.compile(input)!!
                                        s.updatePattern(pattern)
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
                    }

                    ui.field {
                        ui.fluid.button {
                            onClick {
                                song?.stop()
                                song = null
                            }

                            icon.stop()
                            +"Stop"
                        }
                    }

                    UiInputField(cps, { cps = it }) {
                        step(0.01)

                        leftLabel {
                            ui.basic.label { icon.clock(); +"CPS" }
                        }
                    }
                }

                UiTextArea(input, { inputStream(it) })
            }
        }
    }
}
