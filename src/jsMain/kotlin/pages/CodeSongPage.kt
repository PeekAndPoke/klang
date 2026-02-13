package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
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
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.codemirror.CodeHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.lang.delay
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.serialization.builtins.serializer

@Suppress("FunctionName")
fun Tag.CodeSongPage(
    id: String?,
) = comp(
    CodeSongPage.Props(id = id)
) {
    CodeSongPage(it)
}

class CodeSongPage(ctx: Ctx<Props>) : Component<CodeSongPage.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val id: String?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    val defaultCode = """
        import * from "stdlib"
        import * from "strudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val songId get() = props.id ?: "new"

    val builtIn = BuiltInSongs.songs.firstOrNull { it.id == songId }

    val cpsStream = StreamSource(builtIn?.cps ?: 0.5)
        .persistInLocalStorage("song-$songId-cps", Double.serializer())

    val titleStream = StreamSource(builtIn?.title ?: "New Song")
        .persistInLocalStorage("song-$songId-title", String.serializer())

    val codeStream = StreamSource(builtIn?.code ?: defaultCode)
        .persistInLocalStorage("song-$songId-code", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })
    private var playback: StrudelPlayback? by value(null)
    private val isPlaying get() = playback != null

    private val editorRef = ComponentRef.Tracker<CodeMirrorComp>()
    private val highlightBuffer = CodeHighlightBuffer(editorRef)
    private var highlightPerEvent by value(highlightBuffer.maxHighlightsPerEvent) {
        highlightBuffer.maxHighlightsPerEvent = it
    }

    private var title: String by value(titleStream()) { titleStream(it) }

    private var cps: Double by value(cpsStream()) {
        cpsStream(it)
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) { isCodeModified = it != codeStream() }

    private var isCodeModified by value(false)

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        // Update the code
        codeStream(code)
        // set as up to date
        isCodeModified = false

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
        playback?.signals?.clear()
        highlightBuffer.cancelAll()
        playback = null
    }

    override fun VDom.render() {

        ui.fluid.container.with("noise-bg") {
            key = "make-song-page"
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
                                ui.large.circular.white.givenNot(isCodeModified) { disabled }.button {
                                    onClick { onPlay() }
                                    icon.black.redo_alternate()
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
                                    onClick { onStop() }
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
                            UiInputField(highlightPerEvent, { highlightPerEvent = it }) {
                                step(1)

                                appear { large }

                                leftLabel {
                                    ui.basic.label { icon.clock(); +"EVT" }
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
