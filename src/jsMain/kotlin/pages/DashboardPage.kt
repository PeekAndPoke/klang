package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.utils.launch
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
import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.codemirror.CodeHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.lang.delay
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.serialization.builtins.serializer

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

    val cpsStream = StreamSource(0.5)
        .persistInLocalStorage("current-cps", Double.serializer())

    val titleStream = StreamSource("New Song")
        .persistInLocalStorage("current-title", String.serializer())

    val codeStream = StreamSource(defaultCode)
        .persistInLocalStorage("current-song", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })
    private var playback: StrudelPlayback? by value(null)
    private val isPlaying get() = playback != null

    private val editorRef = ComponentRef.Tracker<CodeMirrorComp>()
    private val highlightBuffer = CodeHighlightBuffer(editorRef)

    private var title: String by value(titleStream()) {
        titleStream(it)
    }

    private var cps: Double by value(cpsStream()) {
        cpsStream(it)
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) { codeStream(it) }

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        when (val s = playback) {
            null -> launch {
                if (!loading) {
                    getPlayer().let { p ->
                        val pattern = StrudelPattern.compileRaw(code)!!

                        playback = p.playStrudel(pattern)

                        // Set up live code highlighting via signals
                        playback?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
                            signal.voices.forEach { voiceEvent ->
                                highlightBuffer.scheduleHighlight(voiceEvent)
                            }
                        }

                        // Optional: Listen to other lifecycle signals
                        playback?.signals?.on<KlangPlaybackSignal.PreloadingSamples> { signal ->
                            console.log("Preloading ${signal.count} samples...")
                        }
                        playback?.signals?.on<KlangPlaybackSignal.SamplesPreloaded> { signal ->
                            console.log("Samples loaded in ${signal.durationMs}ms")
                        }

                        playback?.start(
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

    private fun onStop() {
        playback?.stop()
        highlightBuffer.cancelAll()
        playback = null
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

                        ui.top.aligned.column {
                            ui.inverted.vertical.menu {
                                console.log("BuiltInSongs", BuiltInSongs.songs.toTypedArray())

                                BuiltInSongs.songs.forEach { song ->

                                    noui.item {
                                        onClick {
                                            onStop()
                                            title = song.title
                                            cps = song.cps
                                            code = song.code
                                            editorRef { it.setCode(song.code) }
                                        }
                                        +song.title
                                    }
                                }
                            }
                        }

                        ui.bottom.aligned.center.aligned.column {
                            css {
                                minHeight = 100.pct
                                paddingRight = 0.px
                            }

                            div {
                                css { height = 75.px }
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
                                        ui.large.circular.black.button {
                                            onClick {
                                                onPlay()
                                                document.documentElement?.requestFullscreen()
                                            }

                                            if (loading) {
                                                icon.loading.spinner()
                                                +"Loading"
                                            } else {
                                                icon.play()
                                                +"Play"
                                            }
                                        }
                                    } else {
                                        ui.large.circular.basic.black.button {
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
                                                onStop()
                                            }

                                            icon.black.stop()
                                        }
                                }

                                noui.item {
                                    UiInputField(cps, { cps = it }) {
                                        step(0.01)

                                        appear { large }

                                        leftLabel {
                                            ui.basic.label { icon.clock(); +"CPS" }
                                        }
                                    }
                                }

                                noui.item {
                                    UiInputField(title, { title = it }) {
                                        appear { large }

                                        leftLabel {
                                            ui.basic.label { icon.music(); +"Title" }
                                        }
                                    }
                                }

                                noui.item {
                                    ui.large.circular.icon.basic.black.button {
                                        if (document.fullscreenElement != null) {
                                            onClick {
                                                document.exitFullscreen()
                                                launch {
                                                    delay(1000)
                                                    triggerRedraw()
                                                }
                                            }
                                            icon.compress()
                                        } else {
                                            onClick {
                                                document.documentElement?.requestFullscreen()
                                                launch {
                                                    delay(1000)
                                                    triggerRedraw()
                                                }
                                            }
                                            icon.expand()
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
