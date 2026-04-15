package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolRegistry
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.klang.ui.codetools.CodeToolModal
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.utils.windowCtrl
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.pointerevents.PointerEvent

/**
 * Reusable Kraft component that wraps [KlangScriptEditorComp] with all the playback-related
 * integrations: the highlight buffer, hover docs, context menus, tool modals, error diagnostics,
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
): ComponentRef<KlangCodeEditorComp> = comp(
    KlangCodeEditorComp.Props(
        ctrl = ctrl,
        availableLibraries = availableLibraries,
        autoImportedLibraries = autoImportedLibraries,
        maxHighlightsPerEvent = maxHighlightsPerEvent,
        pauseHighlightsWhen = pauseHighlightsWhen,
        extraVoiceHandler = extraVoiceHandler,
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
        /** Called in addition to the internal highlight buffer — useful for parallel highlight surfaces (e.g. a block editor). */
        val extraVoiceHandler: ((KlangPlaybackSignal.VoicesScheduled.VoiceEvent) -> Unit)?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val editorRef = ComponentRef.Tracker<KlangScriptEditorComp>()

    private val highlightBuffer = CodeMirrorHighlightBuffer(
        maxHighlightsPerEvent = props.maxHighlightsPerEvent,
    )

    private val hoverPopup: HoverPopupCtrl by lazy { HoverPopupCtrl(popups = popups) }

    private val hoverContent: FlowContent.(KlangSymbol) -> Unit = { doc ->
        KlangSymbolDocsComp(symbol = doc, onNavigate = ::navToDoc)
    }

    private val state by subscribingTo(props.ctrl.state)

    @Suppress("unused")
    private val signalSub by subscribingTo(props.ctrl.signals) { signal ->
        if (signal is KlangPlaybackSignal.VoicesScheduled && props.pauseHighlightsWhen?.invoke() != true) {
            signal.voices.forEach { voiceEvent ->
                highlightBuffer.scheduleHighlight(voiceEvent)
                props.extraVoiceHandler?.invoke(voiceEvent)
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
            }
            onUnmount {
                highlightBuffer.detach()
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

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun navToDoc(doc: KlangSymbol, event: dynamic) {
        val uri = Nav.manualsLibrarySearch("sprudel", "function:${doc.name}")
        val pointerEvent = event as? PointerEvent
        if (pointerEvent?.shiftKey == true) {
            router.navToUri(pointerEvent, uri)
        } else {
            router.navToUri(uri)
        }
    }

    private fun openTool(toolName: String, toolCtx: KlangUiToolContext, argFrom: Int) {
        val tool = KlangUiToolRegistry.get(toolName) ?: return

        val baseLoc = offsetToSourceLocation(props.ctrl.state().code, argFrom)
        var attrs = toolCtx.attrs.plus(KlangUiToolContext.BaseSourceLocation, baseLoc)

        props.ctrl.playback()?.let { pb ->
            attrs = attrs.plus(KlangUiToolContext.PlaybackVoiceEvents, pb.signals)
        }

        modals.show { handle ->
            CodeToolModal(handle) {
                tool.apply {
                    render(
                        toolCtx.copy(
                            attrs = attrs,
                            onCommit = {
                                toolCtx.onCommit(it)
                                if (props.ctrl.state().isPlaying) props.ctrl.play()
                            },
                            onCancel = { handle.close(); toolCtx.onCancel() }
                        )
                    )
                }
            }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            KlangScriptEditorComp(
                code = state.code,
                onCodeChanged = { newCode -> props.ctrl.setCode(newCode) },
                availableLibraries = props.availableLibraries,
                autoImportedLibraries = props.autoImportedLibraries,
                hoverPopup = hoverPopup,
                hoverContent = hoverContent,
                popups = popups,
                onNavigate = ::navToDoc,
                onOpenTool = { toolName, toolCtx, argFrom, _ ->
                    openTool(toolName = toolName, toolCtx = toolCtx, argFrom = argFrom)
                },
            ).track(editorRef)
        }
    }
}

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
