package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.popups.PopupsManager
import de.peekandpoke.kraft.popups.PopupsManager.Companion.popups
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
import io.peekandpoke.klang.blocks.ui.KlangBlocksHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.codemirror.dslGoToDocsExtension
import io.peekandpoke.klang.codemirror.dslHoverTooltipExtension
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.withEditorErrorHandling
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.lang.strudelLib
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.serialization.builtins.serializer
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Date

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

    val songTitleStream = StreamSource(builtIn?.title ?: "New Song")
        .persistInLocalStorage("song-$songId-title", String.serializer())

    val codeStream = StreamSource(builtIn?.code ?: defaultCode)
        .persistInLocalStorage("song-$songId-code", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })
    private var playback: StrudelPlayback? by value(null)
    private val isPlaying get() = playback != null

    private val codeEditorRef = ComponentRef.Tracker<CodeMirrorComp>()
    private val blocksEditorRef = ComponentRef.Tracker<KlangBlocksEditorComp>()

    private val highlightBuffer = CodeMirrorHighlightBuffer(codeEditorRef)
    private var highlightPerEvent by value(highlightBuffer.maxHighlightsPerEvent) {
        highlightBuffer.maxHighlightsPerEvent = it
    }

    private val blocksHighlightBuffer = KlangBlocksHighlightBuffer()

    private var songTitle: String by value(songTitleStream()) { songTitleStream(it) }

    private var cps: Double by value(cpsStream()) {
        cpsStream(it)
        highlightBuffer.cancelAll()
        blocksHighlightBuffer.cancelAll()
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) {
        isCodeModified = it != codeStream()
    }

    private var isCodeModified by value(false)

    val isBuiltInModified get() = builtIn != null && builtIn.code != code

    /** Current view: text editor or visual block editor. */
    private var editorMode by value(EditorMode.CODE)

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    private fun resetToOriginal() {
        builtIn?.let { b ->
            code = b.code
            codeStream(b.code)
            cps = b.cps
            cpsStream(b.cps)
            songTitle = b.title
            songTitleStream(b.title)

            codeEditorRef { it.setCode(b.code) }
            blocksEditorRef { it.setCode(b.code) }
        }
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
        blocksHighlightBuffer.cancelAll()

        when (val s = playback) {
            null -> launch {
                if (!loading) {
                    withEditorErrorHandling(codeEditorRef) {
                        getPlayer().let { p ->
                            val pattern = StrudelPattern.compileRaw(code)
                                ?: error("Failed to compile Strudel pattern from code")

                            playback = p.playStrudel(pattern)

                            playback?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
                                signal.voices.forEach { voiceEvent ->
                                    highlightBuffer.scheduleHighlight(voiceEvent)
                                    val chain = voiceEvent.sourceLocations as? SourceLocationChain ?: return@forEach
                                    val now = Date.now()
                                    val startFromNowMs = maxOf(1.0, voiceEvent.startTime * 1000.0 - now)
                                    val durationMs = maxOf(200.0, minOf(10000.0, (voiceEvent.endTime - voiceEvent.startTime) * 1000.0))
                                    chain.locations.asReversed().take(highlightPerEvent).forEach { location ->
                                        blocksHighlightBuffer.scheduleHighlight(location, startFromNowMs, durationMs)
                                    }
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
                withEditorErrorHandling(codeEditorRef) {
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
        blocksHighlightBuffer.cancelAll()
        playback = null
    }

    /** True when the current code contains any comments (they would be lost on Code→Blocks). */
    private fun codeHasComments(): Boolean = "//" in code || "/*" in code

    /** Switch to Blocks mode — asks for confirmation first if the code has comments. */
    private fun switchToBlocks(event: PointerEvent) {
        if (codeHasComments()) {
            popups.showContextMenu(event = event, positioning = PopupsManager.Positioning.BottomRight) { handle ->
                ui.compact.segment {
                    css {
                        width = LinearDimension.maxContent
                    }
                    p { +"Comments will be lost when switching to Blocks mode." }
                    ui.mini.basic.button {
                        onClick { handle.close() }
                        +"Cancel"
                    }
                    ui.mini.black.button {
                        onClick { handle.close(); editorMode = EditorMode.BLOCKS }
                        +"Switch anyway"
                    }
                }
            }
        } else {
            editorMode = EditorMode.BLOCKS
        }
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

                    // Fullscreen toggle
                    ui.right.floated.basic.fitted.segment {
                        ui.horizontal.list {
                            noui.item {
                                FullscreenToggleButton(fs = fs)
                            }
                        }
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
                                    title = "Stop playback"
                                    icon.black.stop()
                                }
                        }

                        if (isBuiltInModified) {
                            noui.item {
                                ui.large.circular.white.icon.button {
                                    onClick { resetToOriginal() }
                                    title = "Reset to original code"
                                    icon.undo()
                                }
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
                            UiInputField(songTitle, { songTitle = it }) {
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

                        // Code / Blocks toggle
                        noui.item {
                            ui.large.buttons {
                                ui.large.given(editorMode == EditorMode.CODE) { black }
                                    .givenNot(editorMode == EditorMode.CODE) { basic }
                                    .icon.button {
                                        onClick { switchToCode() }
                                        title = "Switch to code editor"
                                        icon.code()
                                    }

                                ui.large.given(editorMode == EditorMode.BLOCKS) { black }
                                    .givenNot(editorMode == EditorMode.BLOCKS) { basic }
                                    .icon.button {
                                        onClick { switchToBlocks(it) }
                                        title = "Switch to blocks editor"
                                        icon.puzzle_piece()
                                    }
                            }
                        }
                    }
                }

                div {
                    key = "dashboard-form-code"
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        minHeight = 0.px
                        overflowY = Overflow.auto
                        overflowX = Overflow.hidden
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
                                    codeEditorRef { it.setErrors(emptyList()) }
                                },
                                extraExtensions = listOf(
                                    dslHoverTooltipExtension(
                                        docProvider = { KlangDocsRegistry.global.get(it) },
                                        onNavigate = ::navToDoc,
                                        popups = popups,
                                    ),
                                    dslGoToDocsExtension(
                                        docProvider = { KlangDocsRegistry.global.get(it) },
                                        onNavigate = ::navToDoc,
                                    ),
                                ),
                            ).track(codeEditorRef)
                        }

                        EditorMode.BLOCKS -> {
                            KlangBlocksEditorComp(
                                availableLibraries = listOf(stdlibLib, strudelLib),
                                initialCode = code,
                                onCodeChanged = { newCode -> code = newCode },
                                onCodeGenChanged = { result -> blocksHighlightBuffer.codeGenResult = result },
                                highlights = blocksHighlightBuffer.highlights,
                            ).track(blocksEditorRef)
                        }
                    }
                }
            }
        }
    }
}
