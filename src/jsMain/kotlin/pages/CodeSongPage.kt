/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
import io.peekandpoke.klang.version
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
import kotlinx.css.Align
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.Flex
import kotlinx.css.FlexBasis
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.Overflow
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flex
import kotlinx.css.flexDirection
import kotlinx.css.flexShrink
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.minHeight
import kotlinx.css.overflowX
import kotlinx.css.overflowY
import kotlinx.css.padding
import kotlinx.css.paddingLeft
import kotlinx.css.px
import kotlinx.css.vh
import kotlinx.css.width
import kotlinx.html.DIV
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

    private val v by subscribingTo(version.map { it.version })

    val defaultCode = """
        import * from "stdlib"
        import * from "sprudel"

        sound("bd hh sd oh")
    """.trimIndent()

    val songId get() = props.id ?: "new"

    val builtIn = BuiltInSongs.songs.firstOrNull { it.id == songId }

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

    private var highlightPerEvent by value(15) { newValue ->
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
        // Stop (the ctrl resets its stream to null) and live updates invalidate every
        // highlight scheduled ahead for the old pattern
        if (signal == null ||
            signal is KlangPlaybackSignal.PlaybackStopped ||
            signal is KlangPlaybackSignal.PatternUpdated
        ) {
            blocksHighlightBuffer.cancelAll()
        }
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
    @Suppress("unused") // referenced only by the temporarily hidden blocks toggle
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

        ui.fluid.container.with("chrome-bg") {
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
                    // minHeight 0 (NOT overflow:hidden, which would clip the
                    // editor's glow) keeps the form from growing past the 100vh
                    // container — the header stays pinned and only the editor
                    // wrapper scrolls internally.
                    minHeight = 0.px
                }
                // Transparent — shows the page container's chrome-bg, so the
                // rounded editor corner reveals the same surface with no seam
                ui.basic.segment {
                    key = "dashboard-form-segment"

                    css {
                        flexShrink = 0.0
                        // Balanced vertical padding — Fomantic's segment default is
                        // 1em top with our old 0 bottom, which read lopsided
                        put("padding", "13px 14px")
                        // Fomantic gives segments a 1rem bottom margin — that was
                        // the black gap between header and editor
                        put("margin", "0")
                    }

                    ui.horizontal.list {
                        key = "dashboard-form-fields"

                        css {
                            // Centered flex row — also vertically centers the
                            // mixed-height items (buttons, LCD, inputs, icons)
                            display = Display.flex
                            justifyContent = JustifyContent.center
                            alignItems = Align.center
                            put("flex-wrap", "wrap")
                        }

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

                        noui.top.aligned.item {
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

                        // Blocks-editor toggle — hidden for now, the block editor
                        // is not ready to show. Re-enable by uncommenting.
                        // noui.item {
                        //     val isBlocks = editorMode == EditorMode.BLOCKS
                        //     css {
                        //         cursor = Cursor.pointer
                        //         display = Display.inlineBlock
                        //     }
                        //     onClick { switchToBlocks(it) }
                        //     title = "Switch to blocks editor"
                        //     icon.given(isBlocks) { inverted.white }
                        //         .givenNot(isBlocks) { grey }
                        //         .puzzle_piece()
                        // }

                        // Fullscreen toggle
                        noui.item {
                            FullscreenToggleButton(fs = fs)
                        }
                    }
                }

                // Outer FRAME — carries the accent strips, corner radius, glow
                // and black surface. It does NOT scroll, so scrolled editor
                // content can never paint over the frame lines.
                div {
                    key = "dashboard-form-code"
                    css {
                        flex = Flex(1.0, 1.0, FlexBasis.auto)
                        minHeight = 0.px
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        // Accent frame — drawn as 1px background gradient strips
                        // instead of real borders, so each line can fade
                        // independently AND follow the rounded corner:
                        //  · top line fades to 33% alpha over its last 20% of width
                        //  · left line fades to 33% alpha over its last third of height
                        // The 1px paddings keep the scroller off the strips.
                        put("border-top-left-radius", "3px")
                        put("padding-top", "1px")
                        put("padding-left", "1px")
                        put(
                            "background-image",
                            "linear-gradient(to right, ${laf.accent} 0%, ${laf.accent} 80%, ${laf.accent}55 100%)," +
                                    " linear-gradient(to bottom, ${laf.accent} 0%, ${laf.accent} 66%, ${laf.accent}55 100%)"
                        )
                        put("background-repeat", "no-repeat")
                        put("background-size", "100% 1px, 1px 100%")
                        // Black like the editor surface — otherwise any sub-pixel
                        // gap between the frame and the editor shows page chrome
                        put("background-color", "#000000")
                        // Soft accent light from the editor's top and left edges —
                        // dimmed to match the layout's ambient edge light
                        put(
                            "box-shadow",
                            "0 -10px 42px ${laf.accent}23, -10px 0 42px ${laf.accent}23"
                        )
                    }

                    // Inner SCROLLER — clips the editor content just inside the
                    // frame; its small radius hugs the outer curve.
                    div {
                        key = "dashboard-form-code-scroll"
                        css {
                            flex = Flex(1.0, 1.0, FlexBasis.auto)
                            minHeight = 0.px
                            overflowY = Overflow.auto
                            overflowX = Overflow.hidden
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            paddingLeft = 12.px
                            put("border-top-left-radius", "2px")
                        }

                        renderEditor()
                    }
                }
            }
        }
    }

    private fun DIV.renderEditor() {
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
