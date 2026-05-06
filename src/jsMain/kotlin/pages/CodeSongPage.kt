package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.blocks.ui.KlangBlocksEditorComp
import io.peekandpoke.klang.blocks.ui.KlangBlocksHighlightBuffer
import io.peekandpoke.klang.comp.FullscreenToggleButton
import io.peekandpoke.klang.comp.KlangCodeEditorComp
import io.peekandpoke.klang.comp.KlangCodePlaybackCtrl
import io.peekandpoke.klang.comp.KlangSymbolDocsComp
import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.ui.HoverPopupCtrl
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
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Flex
import kotlinx.css.FlexBasis
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flex
import kotlinx.css.flexDirection
import kotlinx.css.flexShrink
import kotlinx.css.height
import kotlinx.css.minHeight
import kotlinx.css.overflow
import kotlinx.css.overflowX
import kotlinx.css.overflowY
import kotlinx.css.padding
import kotlinx.css.paddingBottom
import kotlinx.css.paddingLeft
import kotlinx.css.px
import kotlinx.css.vh
import kotlinx.css.width
import kotlinx.html.FlowContent
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
        import * from "sprudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val songId get() = props.id ?: "new"

    val builtIn = BuiltInSongs.songs.firstOrNull { it.id == songId }

    val v = 2

    val rpmStream = StreamSource(builtIn?.rpm ?: 30.0)
        .persistInLocalStorage("song-$v-$songId-rpm", Double.serializer())

    val songTitleStream = StreamSource(builtIn?.title ?: "New Song")
        .persistInLocalStorage("song-$v-$songId-title", String.serializer())

    val codeStream = StreamSource(builtIn?.code ?: defaultCode)
        .persistInLocalStorage("song-$v-$songId-code", String.serializer())

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val ctrl = KlangCodePlaybackCtrl.builder()
        .code(codeStream())
        .rpm(rpmStream())
        .title(songTitleStream())
        .build()

    @Suppress("unused")
    private val laf by subscribingTo(KlangTheme)
    private val state by subscribingTo(ctrl.state)
    private val currentModals by subscribingTo(modals)

    private val codeEditorRef = ComponentRef.Tracker<KlangCodeEditorComp>()
    private val blocksEditorRef = ComponentRef.Tracker<KlangBlocksEditorComp>()

    private val blocksHighlightBuffer = KlangBlocksHighlightBuffer()

    private var highlightPerEvent by value(10) { newValue ->
        codeEditorRef { it.setMaxHighlightsPerEvent(newValue) }
        blocksHighlightBuffer.cancelAll()
        ctrl.reemitVoiceSignals()
    }

    val isBuiltInModified get() = builtIn != null && builtIn.code != state.code

    /** Current view: text editor or visual block editor. */
    private var editorMode by value(EditorMode.CODE)

    private val hoverPopup: HoverPopupCtrl by lazy { HoverPopupCtrl(popups = popups) }

    private val hoverContent: FlowContent.(KlangSymbol) -> Unit = { doc ->
        KlangSymbolDocsComp(symbol = doc, onNavigate = ::navToDoc)
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

    // Feed voice-scheduled signals into the blocks highlight buffer.
    // The code editor handles its own highlights via KlangCodeEditorComp.
    @Suppress("unused")
    private val blocksVoiceSub by subscribingTo(ctrl.signals) { signal ->
        if (signal is KlangPlaybackSignal.VoicesScheduled && currentModals.isEmpty()) {
            signal.voices.forEach { voiceEvent ->
                val chain = voiceEvent.sourceLocations ?: return@forEach
                val now = Date.now()
                val startFromNowMs = maxOf(1.0, voiceEvent.startTime * 1000.0 - now)
                val durationMs = maxOf(200.0, minOf(10000.0, (voiceEvent.endTime - voiceEvent.startTime) * 1000.0))
                chain.locations.asReversed().take(highlightPerEvent).forEach { location ->
                    blocksHighlightBuffer.scheduleHighlight(location, startFromNowMs, durationMs)
                }
            }
        }
    }

    // On rpm changes: persist to localStorage AND cancel highlights to avoid stale timing across tempo shifts.
    @Suppress("unused")
    private val rpmChange by subscribingTo(ctrl.state.map { it.rpm }.distinct()) { newRpm ->
        rpmStream(newRpm)
        codeEditorRef { editor -> editor.cancelHighlights() }
        blocksHighlightBuffer.cancelAll()
    }

    // When playback stops by any path (button, unmount, exclusive takeover), drop pending highlights.
    @Suppress("unused")
    private val playingChange by subscribingTo(ctrl.state.map { it.isPlaying }.distinct()) { isPlaying ->
        if (!isPlaying) {
            codeEditorRef { editor -> editor.cancelHighlights() }
            blocksHighlightBuffer.cancelAll()
        }
    }

    // Persist title changes back to the localStorage-backed stream.
    @Suppress("unused")
    private val titlePersist by subscribingTo(ctrl.state.map { it.title }.distinct()) { newTitle ->
        if (newTitle != null) songTitleStream(newTitle)
    }

    init {
        lifecycle {
            onMount {
                codeEditorRef { it.setMaxHighlightsPerEvent(highlightPerEvent) }
            }
            onUnmount {
                ctrl.stop()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onPlay() {
        // Persist the current code before starting (matches original strategy of persist-on-play).
        codeStream(ctrl.state().code)
        ctrl.play()
    }

    private fun resetToOriginal() {
        builtIn?.let { b ->
            ctrl.stop()
            ctrl.setCode(b.code)
            ctrl.setRpm(b.rpm)
            ctrl.setTitle(b.title)
            codeStream(b.code)
            codeEditorRef { it.setCode(b.code) }
            blocksEditorRef { it.setCode(b.code) }
        }
    }

    /** True when the current code contains any comments (they would be lost on Code→Blocks). */
    private fun codeHasComments(): Boolean = "//" in state.code || "/*" in state.code

    /** Switch to Blocks mode — asks for confirmation first if the code has comments. */
    private fun switchToBlocks(event: PointerEvent) {
        if (codeHasComments()) {
            popups.showContextMenu(event = event, positioning = PopupsManager.Positioning.BottomCenter) { handle ->
                ui.compact.segment.with(laf.styles.popup()) {
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
                            if (!state.isPlaying) {
                                ui.circular.white.button {
                                    onClick { onPlay() }
                                    if (state.isPlayerLoading) {
                                        icon.black.loading.spinner()
                                        +"Loading"
                                    } else {
                                        icon.play {
                                            css {
                                                put("--icon-glow-color", laf.critical)
                                                put("animation", "iconGlow 2.5s ease-in-out infinite")
                                            }
                                        }
                                        +"Play"
                                    }
                                }
                            } else {
                                ui.circular.white
                                    .givenNot(state.isCodeModified) { disabled }.button {
                                        onClick { onPlay() }
                                        if (state.isCodeModified) {
                                            icon.redo_alternate {
                                                css {
                                                    put("--icon-glow-color", laf.critical)
                                                    put("animation", "iconGlow 2.5s ease-in-out infinite")
                                                }
                                            }
                                        } else {
                                            icon.black.redo_alternate()
                                        }
                                        +"Update"
                                    }
                            }

                            ui.circular.white
                                .givenNot(state.isPlaying) { disabled }
                                .given(state.isPlaying) { white }.icon.button {
                                    onClick { ctrl.stop() }
                                    title = "Stop playback"
                                    icon.black.stop()
                                }
                        }

                        noui.middle.aligned.item {
                            LcdDisplay(value = state.currentCycle, digits = 4, dim = !state.isPlaying)
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

                        // RPM field
                        noui.item {
                            css { width = 140.px }
                            UiInputField(state.rpm, { ctrl.setRpm(it) }) {
                                step(0.5)
                                appear { large }
                                wrapFieldWith { fluid }
                                leftLabel {
                                    ui.grey.label {
                                        title = "Revolutions per minute"
                                        +"RPM"
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
                            UiInputField(state.title ?: "", { ctrl.setTitle(it) }) {
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
                            KlangCodeEditorComp(
                                ctrl = ctrl,
                                availableLibraries = listOf(stdlibLib, sprudelLib),
                                maxHighlightsPerEvent = highlightPerEvent,
                                pauseHighlightsWhen = { currentModals.isNotEmpty() },
                            ).track(codeEditorRef)
                        }

                        EditorMode.BLOCKS -> {
                            KlangBlocksEditorComp(
                                availableLibraries = listOf(stdlibLib, sprudelLib),
                                initialCode = state.code,
                                onCodeChanged = { newCode -> ctrl.setCode(newCode) },
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
