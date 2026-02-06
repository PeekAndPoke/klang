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
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.StreamSource
import de.peekandpoke.ultra.streams.ops.map
import de.peekandpoke.ultra.streams.ops.persistInLocalStorage
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.comp.Oscilloscope
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

    val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })
    var song: StrudelPlayback? by value(null)
    val isPlaying get() = song != null

    val editorRef = ComponentRef.Tracker<CodeMirrorComp>()

    var code: String by value(codeStream()) {
        codeStream(it)
    }

    val cps: Double by subscribingTo(cpsStream) {
        song?.updateCyclesPerSecond(it)
    }

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
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
            ?.takeLast(2)
            ?.forEach { doIt(it) }
    }

    private fun onPlay() {
        when (val s = song) {
            null -> launch {
                if (!loading) {
                    getPlayer().let { p ->
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
                                paddingRight = 0.px
                            }

                            div {
                                css { height = 100.px }
                                Oscilloscope { Player.get() }
                            }

                            ui.basic.inverted.black.fitted.segment {
                                css {
                                    backgroundColor = Color.black
                                    marginTop = 0.px
                                    paddingBottom = 0.px
                                }

                                div {
                                    css { whiteSpace = WhiteSpace.nowrap }
                                    onClick { router.navToUri(Nav.tour()) }

                                    icon.music { css { marginRight = 8.px } }

                                    ui.big.text {
                                        css {
                                            fontFamily = "monospace"
                                            lineHeight = LineHeight("2.0em")
                                            color = Color.white
                                            display = Display.inlineBlock
                                            fontWeight = FontWeight.bold
                                        }
                                        +"KLANG AUDIO MOTÖR"
                                    }

                                    icon.music { css { transform { scaleX(-1.0) }; marginLeft = 8.px } }
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

                            ui.horizontal.list {
                                key = "dashboard-form-fields"

                                noui.item {
                                    if (!isPlaying) {
                                        ui.large.circular.blue.button {
                                            onClick { onPlay() }

                                            if (loading) {
                                                icon.loading.spinner()
                                                +"Loading"
                                            } else {
                                                icon.play()
                                                +"Play"
                                            }
                                        }
                                    } else {
                                        ui.large.circular.inverted.blue.button {
                                            onClick { onPlay() }
                                            icon.redo_alternate()
                                            +"Update"
                                        }
                                    }
                                    ui.large.circular.icon.givenNot(isPlaying) { disabled }
                                        .given(isPlaying) { white }.button {
//                                            onClick {
//                                                song?.stop()
//                                                song = null
//                                            }

                                            icon.black.pause()
                                        }
                                    ui.large.circular.icon.givenNot(isPlaying) { disabled }
                                        .given(isPlaying) { white }.button {
                                            onClick {
                                                song?.stop()
                                                song = null
                                            }

                                            icon.black.stop()
                                        }
                                }

                                noui.item {

                                    UiInputField(cps, { cpsStream(it) }) {
                                        step(0.01)

                                        appear { large }

                                        leftLabel {
                                            ui.basic.label { icon.clock(); +"CPS" }
                                        }
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
