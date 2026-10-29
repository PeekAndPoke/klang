/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.strings.offsetToSourceLocation
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.codemirror.BookmarkOptions
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.klang.ui.codemirror.QuickHelpHandler
import io.peekandpoke.klang.ui.codemirror.ToolCallSite
import io.peekandpoke.klang.visuals.VisualsScreen
import io.peekandpoke.kraft.addons.pixijs.PixiJsAddon
import io.peekandpoke.kraft.addons.pixijs.pixiJs
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.utils.windowCtrl
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.Stream
import kotlinx.browser.window
import kotlinx.css.Position
import kotlinx.css.position
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

/**
 * Reusable Kraft component that wraps [KlangScriptEditorComp] with all the playback-related
 * integrations: the highlight buffer, hover docs, context menus, the Sprudel tool's live context, error diagnostics,
 * and window-focus re-emission.
 *
 * The page owns a [KlangCodePlaybackCtrl] and passes it in via props. The page also renders
 * its own button bar (with buttons driving [KlangCodePlaybackCtrl.play] / [KlangCodePlaybackCtrl.stop] /
 * [KlangCodePlaybackCtrl.setRpm]) and subscribes to the controller's streams for button state.
 */
@Suppress("FunctionName")
fun Tag.KlangCodeEditorComp(
    ctrl: KlangCodePlaybackCtrl,
    availableLibraries: List<KlangScriptLibrary>,
    autoImportedLibraries: List<KlangScriptLibrary> = emptyList(),
    maxHighlightsPerEvent: Int = 10,
    pauseHighlightsWhen: (() -> Boolean)? = null,
    extraVoiceHandler: ((KlangPlaybackSignal.VoicesScheduled.VoiceEvent) -> Unit)? = null,
    currentSource: String? = null,
    showMinimap: Boolean = false,
    bookmarks: BookmarkOptions? = null,
    showKeysOverview: Boolean = false,
    onQuickDocumentation: QuickHelpHandler? = null,
    onHoverDocumentation: QuickHelpHandler? = null,
    onToolCall: ((ToolCallSite?) -> Unit)? = null,
    onShowTool: (() -> Unit)? = null,
): ComponentRef<KlangCodeEditorComp> = comp(
    KlangCodeEditorComp.Props(
        ctrl = ctrl,
        availableLibraries = availableLibraries,
        autoImportedLibraries = autoImportedLibraries,
        maxHighlightsPerEvent = maxHighlightsPerEvent,
        pauseHighlightsWhen = pauseHighlightsWhen,
        extraVoiceHandler = extraVoiceHandler,
        currentSource = currentSource,
        showMinimap = showMinimap,
        bookmarks = bookmarks,
        showKeysOverview = showKeysOverview,
        onQuickDocumentation = onQuickDocumentation,
        onHoverDocumentation = onHoverDocumentation,
        onToolCall = onToolCall,
        onShowTool = onShowTool,
    )
) {
    KlangCodeEditorComp(it)
}

class KlangCodeEditorComp(ctx: Ctx<Props>) : Component<KlangCodeEditorComp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val ctrl: KlangCodePlaybackCtrl,
        val availableLibraries: List<KlangScriptLibrary>,
        val autoImportedLibraries: List<KlangScriptLibrary>,
        val maxHighlightsPerEvent: Int,
        /** Returning `true` suppresses the highlight buffer for the current voice event (e.g. while a modal is open). */
        val pauseHighlightsWhen: (() -> Boolean)?,
        /** Called in addition to the internal highlight buffer, for any parallel highlight surface. */
        val extraVoiceHandler: ((KlangPlaybackSignal.VoicesScheduled.VoiceEvent) -> Unit)?,
        /**
         * Source identity of the file shown in this editor. Highlight events whose
         * [SourceLocation.source] doesn't match are silently dropped — keeps imported-
         * library locations from drawing ghost marks at positions the user can't see.
         * `null` (the default) matches main-script locations.
         */
        val currentSource: String?,
        /** Shows a minimap of the whole document; the host must give the editor a definite height. */
        val showMinimap: Boolean,
        /** The numbered bookmarks and the host that keeps them (KLANG-00323); null leaves the editor without. */
        val bookmarks: BookmarkOptions?,
        /** Shows the editor's keys in its top-right corner while the key hints show (KLANG-00325). */
        val showKeysOverview: Boolean,
        /** Gets the name at the cursor on Alt+Q, for a Quick help panel of the host's (KLANG-00349); null for the popup. */
        val onQuickDocumentation: QuickHelpHandler?,
        /** Gets the name the mouse rests on, for a Quick help panel of the host's (KLANG-00349); null for the popup. */
        val onHoverDocumentation: QuickHelpHandler?,
        /** Gets the call around the cursor whose tools the host's Sprudel tab shows (KLANG-00374); null for none. */
        val onToolCall: ((ToolCallSite?) -> Unit)?,
        /** Opens the host's Sprudel tab, from a right-click's "Open the Sprudel tool" (KLANG-00374); null for no item. */
        val onShowTool: (() -> Unit)?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val editorRef = ComponentRef.Tracker<KlangScriptEditorComp>()

    private val highlightBuffer = CodeMirrorHighlightBuffer(
        maxHighlightsPerEvent = props.maxHighlightsPerEvent,
    )

    /** Lazily-loaded PixiJS addon backing the highlight overlay; arrives asynchronously after mount. */
    private val pixiAddon: PixiJsAddon? by subscribingTo(addons.pixiJs)

    private val hoverPopup: HoverPopupCtrl by lazy { HoverPopupCtrl(popups = popups) }

    private val hoverContent: FlowContent.(KlangSymbol, String?) -> Unit = { doc, declarationCode ->
        KlangSymbolDocsComp(symbol = doc, declarationCode = declarationCode, onNavigate = ::navToDoc)
    }

    private val state by subscribingTo(props.ctrl.state)

    /**
     * Whether a visual covers the window ([VisualsScreen]): no highlights then, none drawn and none scheduled, to save
     * the CPU for the visual (the maintainer, 2026-10-29: "while a visual is open we can stop the code highlights to
     * save cpu"); on leaving, the voices are emitted again and the highlights pick up where the song is.
     */
    private var visualOpen = VisualsScreen.open()

    @Suppress("unused")
    private val visualSub by subscribingTo(VisualsScreen.open) { open ->
        if (open == visualOpen) {
            return@subscribingTo
        }

        visualOpen = open

        if (open) {
            highlightBuffer.cancelAll()
        } else {
            props.ctrl.reemitVoiceSignals()
        }
    }

    @Suppress("unused")
    private val signalSub by subscribingTo(props.ctrl.signals) { signal ->
        when {
            // Stop (the ctrl resets its stream to null) and live updates both invalidate
            // every highlight scheduled ahead for the old pattern
            signal == null ||
                signal is KlangPlaybackSignal.PlaybackStopped ||
                signal is KlangPlaybackSignal.PatternUpdated -> {
                highlightBuffer.cancelAll()
            }

            signal is KlangPlaybackSignal.VoicesScheduled && !visualOpen && props.pauseHighlightsWhen?.invoke() != true -> {
                signal.voices.forEach { voiceEvent ->
                    highlightBuffer.scheduleHighlight(voiceEvent)
                    props.extraVoiceHandler?.invoke(voiceEvent)
                }
            }
        }
    }

    @Suppress("unused")
    private val errorSub by subscribingTo(props.ctrl.errors) { errors ->
        editorRef { it.setErrors(errors) }
    }

    @Suppress("unused")
    private val hasFocus by subscribingTo(windowCtrl.hasFocus) {
        if (it) props.ctrl.reemitVoiceSignals()
    }

    init {
        lifecycle {
            onMount {
                editorRef { editor -> editor.editorView?.let { highlightBuffer.attachTo(it) } }
                highlightBuffer.maxHighlightsPerEvent = props.maxHighlightsPerEvent
                highlightBuffer.currentSource = props.currentSource
                highlightBuffer.setPixiAddon(pixiAddon)
            }
            onUpdate {
                // The pixi addon loads lazily — feed it to the buffer once the subscription emits.
                highlightBuffer.setPixiAddon(pixiAddon)
            }
            onUnmount {
                highlightBuffer.detach()
                replayTimer?.let { window.clearTimeout(it) }
            }
        }
    }

    //  API  ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Push new code into the underlying CodeMirror view. CodeMirror owns its own document state,
     * so a controller-driven reset needs to call this in addition to [KlangCodePlaybackCtrl.setCode].
     */
    fun setCode(newCode: String) {
        editorRef { it.setCode(newCode) }
    }

    /** Updates the highlight-buffer's per-event cap and re-emits current voice signals to pick up the change. */
    fun setMaxHighlightsPerEvent(n: Int) {
        highlightBuffer.maxHighlightsPerEvent = n
        highlightBuffer.cancelAll()
        props.ctrl.reemitVoiceSignals()
    }

    /**
     * Updates the source identity used by the highlight buffer to filter incoming events.
     * Pass `null` to match main-script locations, or a library URI (e.g. `peekandpoke/tetris`)
     * to view-only highlights from that library's source.
     */
    fun setCurrentSource(source: String?) {
        highlightBuffer.currentSource = source
        highlightBuffer.cancelAll()
        props.ctrl.reemitVoiceSignals()
    }

    /** Cancels all pending highlight marks on the code view. */
    fun cancelHighlights() {
        highlightBuffer.cancelAll()
    }

    /**
     * Puts the cursor at [pos] in the code, its line in the middle of the view, the focus in the code: the Quick help's
     * "Used on" list jumps so (KLANG-00349). The list may be older than the text (an edit since): a place past the
     * end goes to the end, never outside the text.
     */
    fun jumpTo(pos: Int) {
        editorRef { editor ->
            editor.editorView?.let { view ->
                val at = pos.coerceIn(0, view.state.doc.length)

                view.dispatch(jsObject<dynamic> {
                    this.selection = jsObject<dynamic> { this.anchor = at }
                    this.effects = EditorView.scrollIntoView(at, jsObject<dynamic> { this.y = "center" })
                })
                view.focus()
            }
        }
    }

    /**
     * Puts the focus into the code view, the cursor and the selection where they were, scrolled into view; nothing when
     * the text has the focus already (an Esc there must not scroll the view back to the cursor). A button inside the
     * editor (the lint panel's close) is not the text: Esc there brings the cursor back.
     */
    fun focus() {
        editorRef { editor ->
            editor.editorView?.takeIf { !it.hasFocus }?.let { view ->
                // CodeMirror's focus does not scroll: a cursor the user scrolled away from comes back into view
                view.dispatch(jsObject<dynamic> { this.scrollIntoView = true })
                view.focus()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun navToDoc(doc: KlangSymbol, event: dynamic) = navToSymbolDocs(doc = doc, event = event)

    /** The playing song's update after a tool's live change, at most every [LIVE_REPLAY_MS]; null while none waits. */
    private var replayTimer: Int? = null

    /**
     * The context for [site]'s tool in the host's Sprudel tab (KLANG-00374): what a tool opened here gets (the
     * playback's voices, the argument's place), its live changes updating a playing song, a few times a second at most.
     */
    fun liveToolContext(site: ToolCallSite): KlangUiToolContext {
        val toolCtx = site.context()
        val baseLoc = offsetToSourceLocation(source = props.ctrl.state().code, offset = site.argFrom)

        // The controller's signals, not one playback's: the tab outlives a Stop and a Play. The tools take only the
        // signals they know by their type, so the stream's null (no playback) passes by unseen
        @Suppress("UNCHECKED_CAST")
        val attrs = toolCtx.attrs
            .plus(KlangUiToolContext.BaseSourceLocation, baseLoc)
            .plus(KlangUiToolContext.PlaybackVoiceEvents, props.ctrl.signals as Stream<KlangPlaybackSignal>)

        fun replaySoon() {
            if (!props.ctrl.state().isPlaying || replayTimer != null) {
                return
            }

            replayTimer = window.setTimeout({
                replayTimer = null

                if (props.ctrl.state().isPlaying) {
                    props.ctrl.play()
                }
            }, LIVE_REPLAY_MS)
        }

        return toolCtx.copy(
            attrs = attrs,
            onCommit = { toolCtx.onCommit(it); replaySoon() },
            call = toolCtx.call?.let { call -> call.copy(onCommitCall = { call.onCommitCall(it); replaySoon() }) },
        )
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            // The anchor of the keys overview, which sits in the editor's corner
            css { position = Position.relative }

            KlangScriptEditorComp(
                code = state.code,
                onCodeChanged = { newCode -> props.ctrl.setCode(newCode) },
                availableLibraries = props.availableLibraries,
                autoImportedLibraries = props.autoImportedLibraries,
                hoverPopup = hoverPopup,
                hoverContent = hoverContent,
                popups = popups,
                onNavigate = ::navToDoc,
                onShowTool = props.onShowTool?.let { show -> { _ -> show() } },
                // The hover's quick help is a setting, on by default (KLANG-00349); read on every move
                hoverDocs = { EditorSettings.hoverHelp() },
                onQuickDocumentation = props.onQuickDocumentation,
                onHoverDocumentation = props.onHoverDocumentation,
                onToolCall = props.onToolCall,
                showMinimap = props.showMinimap,
                bookmarks = props.bookmarks,
            ).track(editorRef)

            if (props.showKeysOverview) {
                // The editor's state as it is now, read when the corner draws (the bookmarks move with the edits)
                EditorKeysCorner(editorState = { editorRef()?.editorView?.state })
            }
        }
    }
}

/** How often a tool's live changes update a playing song at most (KLANG-00374): the music follows a drag. */
private const val LIVE_REPLAY_MS = 250
