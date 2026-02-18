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
import io.peekandpoke.klang.codemirror.EditorError
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
        // store cps
        cpsStream(it)
        // clear highlight buffer
        highlightBuffer.cancelAll()
        // Update the playback
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) {
        isCodeModified = it != codeStream()
    }

    private var isCodeModified by value(false)

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    init {
        lifecycle {
            onUnmount {
                onStop()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        // Update the code
        codeStream(code)
        // set as up to date
        isCodeModified = false
        // clear the highlight buffer
        highlightBuffer.cancelAll()

        when (val s = playback) {
            null -> launch {
                if (!loading) {
                    try {
                        getPlayer().let { p ->
                            val pattern = StrudelPattern.compileRaw(code)
                                ?: error("Failed to compile Strudel pattern from code")

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

                            // Clear errors on success
                            editorRef { it.setErrors(emptyList()) }
                        }
                    } catch (e: Throwable) {
                        console.error("Error compiling/playing pattern:", e)
                        val editorError = mapToEditorError(e)
                        console.log("[CodeSongPage] Mapped error:", editorError)
                        // Set errors via component ref
                        editorRef { it.setErrors(listOf(editorError)) }
                    }
                }
            }

            else -> {
                try {
                    val pattern = StrudelPattern.compileRaw(code)
                        ?: error("Failed to compile Strudel pattern from code")
                    s.updatePattern(pattern)

                    // Clear errors on success
                    editorRef { it.setErrors(emptyList()) }
                } catch (e: Throwable) {
                    console.error("Error updating pattern:", e)
                    val editorError = mapToEditorError(e)
                    console.log("[CodeSongPage] Mapped error:", editorError)
                    // Set errors via component ref
                    editorRef { it.setErrors(listOf(editorError)) }
                }
            }
        }
    }

    private fun onStop() {
        playback?.stop()
        playback?.signals?.clear()
        highlightBuffer.cancelAll()
        playback = null
    }

    /**
     * Map a Throwable to an EditorError
     * Tries to extract line and column information from the exception
     */
    private fun mapToEditorError(e: Throwable): EditorError {
        // Try to extract location info from the exception message
        // Common patterns:
        // - "Parse error at 14:3: Expected expression"
        // - "Error at line X, column Y"
        // - "Line X: error message"
        // - "at line X"

        val message = e.message ?: "Unknown error"

        // Try pattern: "at line:col:" (e.g., "Parse error at 14:3: Expected expression")
        val atLineColRegex = Regex("at\\s+(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
        val atLineColMatch = atLineColRegex.find(message)

        val line: Int
        val col: Int

        if (atLineColMatch != null) {
            line = atLineColMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            col = atLineColMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
        } else {
            // Fallback to separate line and column patterns
            val lineRegex = Regex("line[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)
            val columnRegex = Regex("column[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)

            val lineMatch = lineRegex.find(message)
            val columnMatch = columnRegex.find(message)

            line = lineMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            col = columnMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        }

        // Try to extract a cleaner message (without the location prefix)
        val cleanMessage = message
            .replace(Regex("Parse error at \\d+:\\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("at line \\d+(, column \\d+)?[:\\s]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("line \\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()
            .takeIf { it.isNotEmpty() } ?: message

        // Add line number to the message for display in error panel
        val messageWithLine = "Line $line: $cleanMessage"

        return EditorError(
            message = messageWithLine,
            line = line,
            col = col,
            len = 1
        )
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

//                            ui.large.circular.icon.givenNot(isPlaying) { disabled }
//                                .given(isPlaying) { white }.button {
//                                            onClick {
//                                                song?.stop()
//                                                song = null
//                                            }
//
//                                    icon.black.pause()
//                                }

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
                    CodeMirrorComp(
                        code = code,
                        onCodeChanged = { newCode ->
                            code = newCode
                            // Clear errors when user types
                            editorRef { it.setErrors(emptyList()) }
                        }
                    ).track(editorRef)
                }
            }
        }
    }
}
