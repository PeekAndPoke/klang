package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
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
import io.peekandpoke.klang.blocks.ui.KlangBlocksEditorComp
import io.peekandpoke.klang.codemirror.CodeHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.codemirror.dslGoToDocsExtension
import io.peekandpoke.klang.codemirror.dslHoverTooltipExtension
import io.peekandpoke.klang.comp.withEditorErrorHandling
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.lang.delay
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.serialization.builtins.serializer
import org.w3c.dom.pointerevents.PointerEvent

/** View mode for the editor panel. */
enum class EditorMode { CODE, BLOCKS }

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
        highlightBuffer.cancelAll()
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) {
        isCodeModified = it != codeStream()
    }

    private var isCodeModified by value(false)

    /** Current view: text editor or visual block editor. */
    private var editorMode by value(EditorMode.CODE)

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
        codeStream(code)
        isCodeModified = false
        highlightBuffer.cancelAll()

        when (val s = playback) {
            null -> launch {
                if (!loading) {
                    withEditorErrorHandling(editorRef) {
                        getPlayer().let { p ->
                            val pattern = StrudelPattern.compileRaw(code)
                                ?: error("Failed to compile Strudel pattern from code")

                            playback = p.playStrudel(pattern)

                            playback?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
                                signal.voices.forEach { voiceEvent ->
                                    highlightBuffer.scheduleHighlight(voiceEvent)
                                }
                            }

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
            }

            else -> launch {
                withEditorErrorHandling(editorRef) {
                    val pattern = StrudelPattern.compileRaw(code)
                        ?: error("Failed to compile Strudel pattern from code")
                    s.updatePattern(pattern)
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

    /** Switch to Blocks mode. */
    private fun switchToBlocks() {
        editorMode = EditorMode.BLOCKS
    }

    /** Switch to Code mode. The code state already reflects the latest workspace contents. */
    private fun switchToCode() {
        editorMode = EditorMode.CODE
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {

        ui.fluid.container.with("noise-bg") {
            key = "make-song-page"
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                height = 100.vh
                padding = Padding(0.px)
                backgroundColor = Color.white
            }
            ui.form {
                key = "dashboard-form"
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    flex = Flex(1.0, 1.0, FlexBasis.auto)
                    overflow = Overflow.hidden
                }
                ui.basic.segment {
                    key = "dashboard-form-segment"

                    css {
                        paddingBottom = 0.px
                        flexShrink = 0.0
                    }

                    ui.horizontal.list {
                        key = "dashboard-form-fields"

                        // Play / Update / Stop controls
                        noui.item {
                            if (!isPlaying) {
                                ui.large.circular.black.button {
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
                                ui.large.circular.white.givenNot(isCodeModified) { disabled }.button {
                                    onClick { onPlay() }
                                    icon.black.redo_alternate()
                                    +"Update"
                                }
                            }

                            ui.large.circular.icon.givenNot(isPlaying) { disabled }
                                .given(isPlaying) { white }.button {
                                    onClick { onStop() }
                                    icon.black.stop()
                                }
                        }

                        // CPS field
                        noui.item {
                            UiInputField(cps, { cps = it }) {
                                step(0.01)
                                appear { large }
                                leftLabel {
                                    ui.basic.label { icon.clock(); +"CPS" }
                                }
                            }
                        }

                        // Title field
                        noui.item {
                            UiInputField(title, { title = it }) {
                                appear { large }
                                leftLabel {
                                    ui.basic.label { icon.music(); +"Title" }
                                }
                            }
                        }

                        // Highlight-per-event field
                        noui.item {
                            UiInputField(highlightPerEvent, { highlightPerEvent = it }) {
                                step(1)
                                appear { large }
                                leftLabel {
                                    ui.basic.label { icon.clock(); +"EVT" }
                                }
                            }
                        }

                        // Fullscreen toggle
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

                        // Code / Blocks toggle
                        noui.item {
                            ui.large.circular
                                .given(editorMode == EditorMode.CODE) { black }
                                .givenNot(editorMode == EditorMode.CODE) { basic }
                                .button {
                                    onClick { switchToCode() }
                                    icon.code()
                                    +"Code"
                                }
                            ui.large.circular
                                .given(editorMode == EditorMode.BLOCKS) { black }
                                .givenNot(editorMode == EditorMode.BLOCKS) { basic }
                                .button {
                                    onClick { switchToBlocks() }
                                    icon.puzzle_piece()
                                    +"Blocks"
                                }
                        }
                    }
                }

                div {
                    key = "dashboard-form-code"
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        overflow = Overflow.hidden
                        display = Display.flex
                        flexDirection = FlexDirection.column
                    }

                    fun navToDoc(doc: KlangSymbol, event: PointerEvent) {
                        val uri = Nav.docsStrudelSearch("function:${doc.name}")
                        if (event.shiftKey) {
                            router.navToUri(event, uri)
                        } else {
                            router.navToUri(uri)
                        }
                    }

                    when (editorMode) {
                        EditorMode.CODE -> {
                            CodeMirrorComp(
                                code = code,
                                onCodeChanged = { newCode ->
                                    code = newCode
                                    editorRef { it.setErrors(emptyList()) }
                                },
                                extraExtensions = listOf(
                                    dslHoverTooltipExtension(
                                        docProvider = { KlangDocsRegistry.global.get(it) },
                                        onNavigate = ::navToDoc,
                                    ),
                                    dslGoToDocsExtension(
                                        docProvider = { KlangDocsRegistry.global.get(it) },
                                        onNavigate = ::navToDoc,
                                    ),
                                ),
                            ).track(editorRef)
                        }

                        EditorMode.BLOCKS -> {
                            KlangBlocksEditorComp(
                                onCodeChanged = { newCode ->
                                    code = newCode
                                    codeStream(newCode)
                                    isCodeModified = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
