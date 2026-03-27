package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.blocks.ui.KlangBlocksEditorComp
import io.peekandpoke.klang.blocks.ui.KlangBlocksHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.KlangSymbolDocsComp
import io.peekandpoke.klang.comp.withEditorErrorHandling
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.klang.ui.codetools.CodeToolModal
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.utils.documentCtrl
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlinx.css.*
import kotlinx.html.*
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
        import * from "sprudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val songId get() = props.id ?: "new"

    val builtIn = BuiltInSongs.songs.firstOrNull { it.id == songId }

    val v = 2

    val cpsStream = StreamSource(builtIn?.cps ?: 0.5)
        .persistInLocalStorage("song-$v-$songId-cps", Double.serializer())

    val songTitleStream = StreamSource(builtIn?.title ?: "New Song")
        .persistInLocalStorage("song-$v-$songId-title", String.serializer())

    val codeStream = StreamSource(builtIn?.code ?: defaultCode)
        .persistInLocalStorage("song-$v-$songId-code", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    @Suppress("unused")
    private val laf by subscribingTo(KlangTheme)
    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })
    private var playback: KlangCyclicPlayback? by value(null)
    private val isPlaying get() = playback != null

    private val codeEditorRef = ComponentRef.Tracker<KlangScriptEditorComp>()
    private val blocksEditorRef = ComponentRef.Tracker<KlangBlocksEditorComp>()

    private val currentModals by subscribingTo(modals)

    private val codeHighlightBuffer = CodeMirrorHighlightBuffer()

    private var highlightPerEvent by value(10) {
        codeHighlightBuffer.maxHighlightsPerEvent = it
        cancelHighlights()
        playback?.reemitVoiceSignals()
    }

    private val blocksHighlightBuffer = KlangBlocksHighlightBuffer()

    private var songTitle: String by value(songTitleStream()) { songTitleStream(it) }

    private var cps: Double by value(cpsStream()) {
        cpsStream(it)
        cancelHighlights()
        playback?.updateCyclesPerSecond(it)
    }

    private var code: String by value(codeStream()) {
        isCodeModified = it != codeStream()
    }

    private var isCodeModified by value(false)
    private var currentCycle: Int by value(0)

    val isBuiltInModified get() = builtIn != null && builtIn.code != code

    /** Current view: text editor or visual block editor. */
    private var editorMode by value(EditorMode.CODE)

    private val hoverPopup: HoverPopupCtrl by lazy { HoverPopupCtrl(popups = popups) }

    private val hoverContent: FlowContent.(KlangSymbol) -> Unit = { doc ->
        KlangSymbolDocsComp(symbol = doc, onNavigate = ::navToDoc)
    }

    private fun openTool(toolName: String, ctx: KlangUiToolContext, argFrom: Int) {
        val tool = KlangUiToolRegistry.get(toolName) ?: return

        // Base source location of the opening quote — used by editors to match voice events
        val baseLoc = offsetToSourceLocation(code, argFrom)
        var attrs = ctx.attrs.plus(KlangUiToolContext.BaseSourceLocation, baseLoc)

        // If playback is active, attach the raw signal stream
        playback?.let { pb ->
            attrs = attrs.plus(KlangUiToolContext.PlaybackVoiceEvents, pb.signals)
        }

        modals.show { handle ->
            CodeToolModal(handle) {
                tool.apply {
                    render(
                        ctx.copy(
                            attrs = attrs,
                            onCommit = {
                                // Update the code
                                ctx.onCommit(it)
                                // update the playback if playing
                                updatePlayback()
                            },
                            onCancel = { handle.close(); ctx.onCancel() }
                        )
                    )
                }
            }
        }
    }

    private fun navToDoc(doc: KlangSymbol, event: dynamic) {
        val uri = Nav.manualsLibrarySearch("sprudel", "function:${doc.name}")
        val pointerEvent = event as? PointerEvent
        if (pointerEvent?.shiftKey == true) {
            router.navToUri(pointerEvent, uri)
        } else {
            router.navToUri(uri)
        }
    }

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

    @Suppress("unused")
    private val hasFocus by subscribingTo(documentCtrl.hasFocus) {
        if (it) playback?.reemitVoiceSignals()
    }

    init {
        lifecycle {
            onMount {
                codeEditorRef { editor -> editor.editorView?.let { codeHighlightBuffer.attachTo(it) } }
                codeHighlightBuffer.maxHighlightsPerEvent = highlightPerEvent
            }
            onUnmount {
                onStop()
                codeHighlightBuffer.detach()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun updatePlayback() {
        if (isPlaying) {
            onPlay()
        }
    }

    private fun cancelHighlights() {
        codeHighlightBuffer.cancelAll()
        blocksHighlightBuffer.cancelAll()
    }

    private fun scheduleVoiceHighlights(voiceEvent: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        codeHighlightBuffer.scheduleHighlight(voiceEvent)
        val chain = voiceEvent.sourceLocations ?: return
        val now = Date.now()
        val startFromNowMs = maxOf(1.0, voiceEvent.startTime * 1000.0 - now)
        val durationMs = maxOf(200.0, minOf(10000.0, (voiceEvent.endTime - voiceEvent.startTime) * 1000.0))
        chain.locations.asReversed().take(highlightPerEvent).forEach { location ->
            blocksHighlightBuffer.scheduleHighlight(location, startFromNowMs, durationMs)
        }
    }

    private fun onPlay() {
        codeStream(code)
        isCodeModified = false
        cancelHighlights()

        when (val s = playback) {
            null -> launch {
                if (!loading) {
                    withEditorErrorHandling(codeEditorRef) {
                        getPlayer().let { p ->
                            val pattern = SprudelPattern.compileRaw(code)
                                ?: error("Failed to compile Sprudel pattern from code")

                            playback = p.play(pattern)

                            currentCycle = 0

                            playback?.signals?.invoke { signal ->
                                when (signal) {
                                    is KlangPlaybackSignal.CycleCompleted -> {
                                        currentCycle = signal.cycleIndex + 1
                                    }

                                    is KlangPlaybackSignal.VoicesScheduled -> {
                                        // When there is a modal dialog open, we stop highlighting
                                        if (currentModals.isNotEmpty()) return@invoke

                                        signal.voices.forEach { scheduleVoiceHighlights(it) }
                                    }

                                    is KlangPlaybackSignal.PreloadingSamples -> {
                                        console.log("Preloading ${signal.count} samples...")
                                    }

                                    is KlangPlaybackSignal.SamplesPreloaded -> {
                                        console.log("Samples loaded in ${signal.durationMs}ms")
                                    }

                                    else -> {}
                                }
                            }

                            playback?.start(
                                KlangCyclicPlayback.Options(cyclesPerSecond = cps)
                            )
                        }
                    }
                }
            }

            else -> launch {
                withEditorErrorHandling(codeEditorRef) {
                    val pattern = SprudelPattern.compileRaw(code)
                        ?: error("Failed to compile Sprudel pattern from code")
                    s.updatePattern(pattern)
                }
            }
        }
    }

    private fun onStop() {
        playback?.stop()
        cancelHighlights()
        playback = null
        currentCycle = 0
    }

    /** True when the current code contains any comments (they would be lost on Code→Blocks). */
    private fun codeHasComments(): Boolean = "//" in code || "/*" in code

    /** Switch to Blocks mode — asks for confirmation first if the code has comments. */
    private fun switchToBlocks(event: PointerEvent) {
        if (codeHasComments()) {
            popups.showContextMenu(event = event, positioning = PopupsManager.Positioning.BottomCenter) { handle ->
                ui.compact.segment {
                    css {
                        width = LinearDimension.maxContent
                    }
                    p { +"Comments will be lost when switching to Blocks mode." }

                    ui.right.aligned.basic.fitted.segment {
                        ui.mini.basic.inverted.button {
                            onClick { handle.close() }
                            icon.times()
                            +"Cancel"
                        }
                        ui.mini.positive.button {
                            onClick { handle.close(); editorMode = EditorMode.BLOCKS }
                            icon.check()
                            +"Switch anyway"
                        }
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
                                ui.circular.white.button {
                                    onClick { onPlay() }
                                    if (loading) {
                                        icon.black.loading.spinner()
                                        +"Loading"
                                    } else {
                                        icon.black.play()
                                        +"Play"
                                    }
                                }
                            } else {
                                ui.circular.white
                                    .givenNot(isCodeModified) { disabled }.button {
                                        onClick { updatePlayback() }
                                        icon.black.redo_alternate()
                                        +"Update"
                                    }
                            }

                            ui.circular.white
                                .givenNot(isPlaying) { disabled }
                                .given(isPlaying) { white }.icon.button {
                                    onClick { onStop() }
                                    title = "Stop playback"
                                    icon.black.stop()
                                }
                        }

                        if (isPlaying) {
                            noui.item {
                                css {
                                    alignSelf = Align.center
                                    color = Color.grey
                                }
                                icon.music()
                                +" Cycle $currentCycle"
                            }
                        }

                        if (isBuiltInModified) {
                            noui.item {
                                ui.circular.white.icon.button {
                                    onClick { resetToOriginal() }
                                    title = "Reset to original code"
                                    icon.black.undo()
                                }
                            }
                        }

                        // CPS field
                        noui.item {
                            css { width = 140.px }
                            UiInputField(cps, { cps = it }) {
                                step(0.01)
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    ui.grey.label {
                                        title = "Cycles per second"
                                        +"CPS"
                                    }
                                }
                            }
                        }

                        // Highlight-per-event field
                        noui.item {
                            css { width = 140.px }
                            UiInputField(highlightPerEvent, { highlightPerEvent = it }) {
                                step(1)
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    ui.grey.label {
                                        title = "Max highlights per audio event"
                                        +"EVT"
                                    }
                                }
                            }
                        }

                        // Title field
                        noui.item {
                            css { width = 300.px }
                            UiInputField(songTitle, { songTitle = it }) {
                                placeholder("Song title")
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    title = "Song title"
                                    ui.grey.label { +"Title" }
                                }
                            }
                        }

                        // Code / Blocks toggle
                        noui.item {
                            val isCode = editorMode == EditorMode.CODE
                            css {
                                cursor = Cursor.pointer
                                display = Display.inlineBlock
                            }
                            onClick { switchToCode() }
                            title = "Switch to code editor"
                            icon.given(isCode) { inverted.white }
                                .givenNot(isCode) { grey }
                                .code()
                        }

                        noui.item {
                            val isBlocks = editorMode == EditorMode.BLOCKS
                            css {
                                cursor = Cursor.pointer
                                display = Display.inlineBlock
                            }
                            onClick { switchToBlocks(it) }
                            title = "Switch to blocks editor"
                            icon.given(isBlocks) { inverted.white }
                                .givenNot(isBlocks) { grey }
                                .puzzle_piece()
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
                        paddingLeft = 12.px
                    }

                    when (editorMode) {
                        EditorMode.CODE -> {
                            KlangScriptEditorComp(
                                code = code,
                                onCodeChanged = { newCode ->
                                    code = newCode
                                    codeEditorRef { it.setErrors(emptyList()) }
                                },
                                availableLibraries = listOf(stdlibLib, sprudelLib),
                                hoverPopup = hoverPopup,
                                hoverContent = hoverContent,
                                popups = popups,
                                onNavigate = ::navToDoc,
                                onOpenTool = { toolName, ctx, argFrom, _ ->
                                    openTool(toolName = toolName, ctx = ctx, argFrom = argFrom)
                                },
                            ).track(codeEditorRef)
                        }

                        EditorMode.BLOCKS -> {
                            KlangBlocksEditorComp(
                                availableLibraries = listOf(stdlibLib, sprudelLib),
                                initialCode = code,
                                onCodeChanged = { newCode -> code = newCode },
                                onCodeGenChanged = { result -> blocksHighlightBuffer.codeGenResult = result },
                                highlights = blocksHighlightBuffer.highlights,
                                hoverPopup = hoverPopup,
                                hoverContent = hoverContent,
                            ).track(blocksEditorRef)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Converts a 0-based document [offset] to a [SourceLocation] pointing at that character.
 *
 * Used once when opening a tool to record the base position of the opening quote.
 */
private fun offsetToSourceLocation(source: String, offset: Int): SourceLocation {
    var line = 1
    var col = 1
    for (i in 0 until offset.coerceAtMost(source.length)) {
        if (source[i] == '\n') {
            line++; col = 1
        } else {
            col++
        }
    }
    return SourceLocation(source = null, startLine = line, startColumn = col, endLine = line, endColumn = col)
}
