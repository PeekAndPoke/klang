package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.utils.setTimeout
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.ops.persistInLocalStorage
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.strudel.ScheduledVoiceEvent
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.serialization.builtins.serializer
import kotlin.js.Date

@Suppress("FunctionName")
fun Tag.DashboardPage() = comp {
    DashboardPage(it)
}

class DashboardPage(ctx: NoProps) : PureComponent(ctx) {

    val defaultCode = """
        import * from "stdlib"
        import * from "strudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val codeStream = StreamSource(defaultCode)
        .persistInLocalStorage("current-song", String.serializer())

    val cpsStream = StreamSource(0.5).persistInLocalStorage("current-cps", Double.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    var loading: Boolean by value(false)
    var player: KlangPlayer? by value(null)
    var song: StrudelPlayback? by value(null)

    val editorRef = ComponentRef.Tracker<CodeMirrorComp>()

    var code: String by value(codeStream()) {
        codeStream(it)
    }

    val cps: Double by subscribingTo(cpsStream) {
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

    private fun scheduleHighlight(event: ScheduledVoiceEvent) {
        // console.log("Voice scheduled:", event.startTime, event.endTime, Date.now())

        fun doIt(location: SourceLocation) {
            val now = Date.now()
            // few ms early for better visuals
            val startFromNowMs = maxOf(1.0, (event.startTimeMs - now) - 25.0)

            // console.log("startFromNowMs", startFromNowMs)

            setTimeout(startFromNowMs.toInt()) {
                editorRef { editor ->
                    // console.log("here", editor)

                    val highlightId = editor.addHighlight(
                        line = location.startLine,
                        column = location.startColumn,
                        length = if (location.startLine == location.endLine) {
                            location.endColumn - location.startColumn
                        } else {
                            // For multiline locations, just highlight the first line for now
                            // TODO: support multiline highlighting
                            2
                        }
                    )

                    // Remove highlight after duration
                    val now = Date.now()
                    val endFromNowMs = maxOf(250.0, event.endTimeMs - now)

                    setTimeout(endFromNowMs.toInt()) {
                        editorRef { it.removeHighlight(highlightId) }
                    }
                }
            }
        }

        // Highlight in editor
        event.sourceLocations
            ?.locations
            ?.takeLast(5)
            ?.forEach { doIt(it) }
    }

    private fun onPlay() {
        when (val s = song) {
            null -> launch {
                if (!loading) {
                    createPlayer().let { p ->
                        val pattern = StrudelPattern.compileRaw(code)!!

                        song = p.playStrudel(pattern)

                        // Set up live code highlighting callback
                        song?.onVoiceScheduled = { event -> scheduleHighlight(event) }

                        song?.start(
                            StrudelPlayback.Options(cyclesPerSecond = cps)
                        )
                    }
                }
            }

            else -> {
                val pattern = StrudelPattern.compileRaw(code)!!
                s.updatePattern(pattern)
            }
        }
    }

    override fun VDom.render() {

        ui.fluid.container {

            ui.grid {
                css {
                    marginTop = 0.px
                }

                ui.three.wide.column {

                    ui.one.column.grid {
                        css {
                            minHeight = 100.vh
                            backgroundColor = Color.black
                        }
                        ui.bottom.aligned.center.aligned.column {
                            css {
                                minHeight = 100.pct
                            }

                            ui.basic.inverted.black.segment {
                                css {
                                    backgroundColor = Color.black
                                    paddingBottom = 0.px
                                }

                                div {
                                    css {
                                        whiteSpace = WhiteSpace.nowrap
                                    }

                                    onClick {
                                        router.navToUri(Nav.tour())
                                    }

                                    icon.music {
                                        css {
                                            marginRight = 8.px
                                        }
                                    }

                                    ui.small.header H1 {
                                        css {
                                            fontFamily = "monospace"
                                            lineHeight = LineHeight("2.0em")
                                            color = Color.white
                                            display = Display.inlineBlock
                                        }
                                        +"KLANG AUDIO MOTÖR"
                                    }

                                    icon.music {
                                        css {
                                            transform { scaleX(-1.0) }
                                            marginLeft = 8.px
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ui.thirteen.wide.column {
                    css {
                        padding = Padding(0.px)
                        backgroundColor = Color.white
                    }
                    ui.form {
                        key = "dashboard-form"
                        ui.basic.segment {
                            key = "dashboard-form-segment"

                            css {
                                paddingBottom = 0.px
                            }

                            ui.six.fields.fields {
                                key = "dashboard-form-fields"

                                ui.field {
                                    ui.fluid.button {
                                        onClick {
                                            onPlay()
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

                                UiInputField(cps, { cpsStream(it) }) {
                                    step(0.01)

                                    leftLabel {
                                        ui.basic.label { icon.clock(); +"CPS" }
                                    }
                                }
                            }
                        }


                        div {
                            key = "dashboard-form-code"

                            // CodeMirror editor container
                            CodeMirrorComp(code = code, onCodeChanged = { code = it })
                                .track(editorRef)
                        }
                    }
                }
            }
        }

    }
}
