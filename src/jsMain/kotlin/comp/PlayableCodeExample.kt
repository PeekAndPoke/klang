package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.common.SourceLocation
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
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.utils.windowCtrl
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.css.Align
import kotlinx.css.Border
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.Overflow
import kotlinx.css.alignSelf
import kotlinx.css.border
import kotlinx.css.borderBottom
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.overflow
import kotlinx.css.paddingLeft
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.PlayableCodeExample(
    code: String,
    rpm: Double = 30.0,
) = comp(
    PlayableCodeExample.Props(code = code, rpm = rpm)
) {
    PlayableCodeExample(it)
}

class PlayableCodeExample(ctx: Ctx<Props>) : Component<PlayableCodeExample.Props>(ctx) {

    companion object {
        private val instances = mutableSetOf<PlayableCodeExample>()

        private fun stopAllExcept(current: PlayableCodeExample) {
            instances.forEach { if (it !== current) it.stopPlayback() }
        }
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val rpm: Double = 30.0,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playback: KlangCyclicPlayback? by value(null)
    private val isPlaying get() = playback != null

    private var rpm: Double by value(props.rpm) {
        playback?.updateRpm(it)
    }

    private val editorRef = ComponentRef.Tracker<KlangScriptEditorComp>()
    private val highlightBuffer = CodeMirrorHighlightBuffer()

    private var currentCode: String by value(props.code)
    private var playingCode: String? by value(null)

    // True when editor code differs from what's currently playing
    private val isModified get() = playingCode != null && currentCode != playingCode

    // True when editor code differs from original props (for reset button)
    private val isModifiedFromOriginal get() = currentCode != props.code

    private var currentCycle: Int by value(0)

    private val laf by subscribingTo(KlangTheme)
    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })

    private val hoverPopup: HoverPopupCtrl by lazy { HoverPopupCtrl(popups = popups) }

    private val hoverContent: FlowContent.(KlangSymbol) -> Unit = { doc ->
        KlangSymbolDocsComp(symbol = doc, onNavigate = ::navToDoc)
    }

    private fun navToDoc(doc: KlangSymbol, event: dynamic) {
        val uri = Nav.manualsLibrarySearch("sprudel", "function:${doc.name}")
        val pointerEvent = event as? org.w3c.dom.pointerevents.PointerEvent
        if (pointerEvent?.shiftKey == true) {
            router.navToUri(pointerEvent, uri)
        } else {
            router.navToUri(uri)
        }
    }

    @Suppress("unused")
    private val hasFocus by subscribingTo(windowCtrl.hasFocus) {
        if (it) playback?.reemitVoiceSignals()
    }

    init {
        lifecycle {
            onMount {
                instances.add(this@PlayableCodeExample)
                editorRef { editor -> editor.editorView?.let { highlightBuffer.attachTo(it) } }
            }
            onUnmount {
                instances.remove(this@PlayableCodeExample)
                stopPlayback()
                highlightBuffer.detach()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    private fun play() {
        stopAllExcept(this)
        highlightBuffer.cancelAll()

        launch {
            withEditorErrorHandling(editorRef) {
                console.log("PlayableCodeExample: Compiling code:", currentCode)

                // Use Player.createEngine() so Osc.register() works, then compile with accurate source locations
                val engine = Player.createEngine()
                val pattern = SprudelPattern.compile(engine, currentCode)

                if (pattern == null) {
                    console.error("PlayableCodeExample: Pattern compilation returned null for code:", currentCode)
                    return@withEditorErrorHandling
                }

                console.log("PlayableCodeExample: Pattern compiled successfully")

                when (val currentPlayback = playback) {
                    null -> {
                        // First play - create new playback
                        console.log("PlayableCodeExample: Getting player...")
                        val player = getPlayer()
                        console.log("PlayableCodeExample: Starting new playback...")

                        playback = player.play(pattern)
                        playback?.start(KlangCyclicPlayback.Options(rpm = rpm))

                        // Mark code as playing
                        playingCode = currentCode

                        console.log("PlayableCodeExample: Playback started, setting up signals...")

                        // Reset cycle counter
                        currentCycle = 0

                        // Set up cycle counter and code highlighting
                        playback?.signals?.invoke { signal ->
                            when (signal) {
                                is KlangPlaybackSignal.CycleCompleted -> {
                                    currentCycle = signal.cycleIndex + 1
                                }

                                is KlangPlaybackSignal.VoicesScheduled -> {
                                    signal.voices.forEach { voiceEvent ->
                                        highlightBuffer.scheduleHighlight(voiceEvent)
                                    }
                                }

                                else -> {}
                            }
                        }

                        console.log("PlayableCodeExample: Play setup complete!")
                    }

                    else -> {
                        // Update existing playback
                        console.log("PlayableCodeExample: Updating pattern on existing playback...")
                        currentPlayback.updatePattern(pattern)

                        // Mark code as playing
                        playingCode = currentCode

                        console.log("PlayableCodeExample: Pattern updated!")
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        playback?.stop()
        playback = null
        highlightBuffer.cancelAll()
        currentCycle = 0
        playingCode = null
    }

    private fun openTool(toolName: String, ctx: KlangUiToolContext, argFrom: Int) {
        val tool = KlangUiToolRegistry.get(toolName) ?: return

        val baseLoc = offsetToSourceLocation(currentCode, argFrom)
        var attrs = ctx.attrs.plus(KlangUiToolContext.BaseSourceLocation, baseLoc)

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
                                ctx.onCommit(it)
                                if (isPlaying) play()
                            },
                            onCancel = { handle.close(); ctx.onCancel() }
                        )
                    )
                }
            }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = laf.styles.darken10()) {
            key = "container"

            css {
                border = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                borderRadius = 4.px
                overflow = Overflow.hidden
                marginTop = 0.5.rem
                marginBottom = 0.5.rem
            }

            // Control bar
            ui.mini.form {
                key = "controlBar"

                css {
                    borderBottom = Border(1.px, BorderStyle.solid, Color(laf.textTertiary))
                    paddingLeft = 0.5.rem
                }

                ui.horizontal.list {
                    key = "controlBarItems"

                    // Play / Update button
                    noui.item {
                        if (!isPlaying) {
                            ui.small.circular.white.button {
                                onClick { play() }
                                if (loading) {
                                    icon.loading.spinner()
                                    +"Loading"
                                } else {
                                    icon.play()
                                    +"Play"
                                }
                            }
                        } else {
                            ui.small.circular.white.givenNot(isModified) { disabled }.button {
                                onClick { play() }
                                icon.black.redo_alternate()
                                +"Update"
                            }
                        }
                    }

                    // Stop button
                    noui.item {
                        ui.small.circular.icon.givenNot(isPlaying) { disabled }.button {
                            onClick { stopPlayback() }
                            icon.black.stop()
                        }
                    }

                    // Reset button (only show if modified from original)
                    noui.item {
                        ui.small.circular.givenNot(isModifiedFromOriginal) { disabled }.button {
                            onClick {
                                stopPlayback()
                                currentCode = props.code
                                editorRef { it.setCode(props.code) }
                            }
                            icon.undo()
                            +"Reset"
                        }
                    }

                    // RPM field
                    noui.item {
                        css { width = 150.px }
                        UiInputField(rpm, { rpm = it }) {
                            step(0.5)
                            wrapFieldWith { fluid }
                            leftLabel {
                                ui.grey.label { +"RPM" }
                            }
                        }
                    }

                    // Info text
                    noui.item {
                        css {
                            alignSelf = Align.center
                            color = Color.grey
                        }
                        if (isPlaying) {
                            icon.music()
                            +" Playing - Cycle $currentCycle"
                        } else {
                            +"Try this example"
                        }
                    }
                }
            }


            // Code editor
            div {
                key = "editor"

                KlangScriptEditorComp(
                    code = currentCode,
                    onCodeChanged = { newCode ->
                        currentCode = newCode
                        editorRef { it.setErrors(emptyList()) }
                    },
                    availableLibraries = listOf(stdlibLib, sprudelLib),
                    autoImportedLibraries = listOf(stdlibLib, sprudelLib),
                    hoverPopup = hoverPopup,
                    hoverContent = hoverContent,
                    popups = popups,
                    onNavigate = ::navToDoc,
                    onOpenTool = { toolName, ctx, argFrom, _ ->
                        openTool(toolName = toolName, ctx = ctx, argFrom = argFrom)
                    },
                ).track(editorRef)
            }
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
