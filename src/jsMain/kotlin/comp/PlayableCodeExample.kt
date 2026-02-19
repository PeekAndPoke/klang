package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.codemirror.CodeHighlightBuffer
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.playStrudel
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.PlayableCodeExample(
    code: String,
) = comp(
    PlayableCodeExample.Props(code = code)
) {
    PlayableCodeExample(it)
}

class PlayableCodeExample(ctx: Ctx<Props>) : Component<PlayableCodeExample.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var playback: StrudelPlayback? by value(null)
    private val isPlaying get() = playback != null

    private val editorRef = ComponentRef.Tracker<CodeMirrorComp>()
    private val highlightBuffer = CodeHighlightBuffer(editorRef)

    private var currentCode: String by value(props.code)
    private var playingCode: String? by value(null)

    // True when editor code differs from what's currently playing
    private val isModified get() = playingCode != null && currentCode != playingCode

    // True when editor code differs from original props (for reset button)
    private val isModifiedFromOriginal get() = currentCode != props.code

    private var currentCycle: Long by value(0)

    private val loading: Boolean by subscribingTo(Player.status.map { it == Player.Status.LOADING })

    init {
        lifecycle {
            onUnmount {
                stopPlayback()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private suspend fun getPlayer(): KlangPlayer {
        return Player.ensure().await()
    }

    private fun play() {
        highlightBuffer.cancelAll()

        launch {
            try {
                console.log("PlayableCodeExample: Compiling code:", currentCode)

                // Use compile() to maintain accurate source locations
                // This pre-executes imports without prepending lines, so highlighting line numbers match the editor
                val pattern = StrudelPattern.compile(currentCode)

                if (pattern == null) {
                    console.error("PlayableCodeExample: Pattern compilation returned null for code:", currentCode)
                    return@launch
                }

                console.log("PlayableCodeExample: Pattern compiled successfully")

                when (val currentPlayback = playback) {
                    null -> {
                        // First play - create new playback
                        console.log("PlayableCodeExample: Getting player...")
                        val player = getPlayer()
                        console.log("PlayableCodeExample: Starting new playback...")

                        playback = player.playStrudel(pattern)
                        playback?.start()

                        // Mark code as playing
                        playingCode = currentCode

                        console.log("PlayableCodeExample: Playback started, setting up signals...")

                        // Reset cycle counter
                        currentCycle = 0

                        // Set up cycle counter
                        playback?.signals?.on<KlangPlaybackSignal.CycleCompleted> { signal ->
                            currentCycle =
                                signal.cycleIndex + 1 // +1 because we want to show the current cycle being played
                        }

                        // Set up code highlighting
                        playback?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
                            signal.voices.forEach { voiceEvent ->
                                highlightBuffer.scheduleHighlight(voiceEvent)
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

            } catch (e: Exception) {
                console.error("PlayableCodeExample: Failed to play example", e)
                e.printStackTrace()
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

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            css {
                border = Border(1.px, BorderStyle.solid, Color("#ddd"))
                borderRadius = 4.px
                overflow = Overflow.hidden
                marginTop = 0.5.rem
                marginBottom = 0.5.rem
            }

            // Control bar
            div {
                css {
                    backgroundColor = Color("#f8f8f8")
                    borderBottom = Border(1.px, BorderStyle.solid, Color("#ddd"))
                    padding = Padding(0.5.rem)
                    display = Display.flex
                    gap = 0.5.rem
                }

                // Play / Update button
                if (!isPlaying) {
                    ui.mini.circular.black.button {
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
                    ui.mini.circular.white.givenNot(isModified) { disabled }.button {
                        onClick { play() }
                        icon.black.redo_alternate()
                        +"Update"
                    }
                }

                // Stop button
                ui.mini.circular.icon.givenNot(isPlaying) { disabled }
                    .given(isPlaying) { white }.button {
                        onClick { stopPlayback() }
                        icon.black.stop()
                    }

                // Reset button (only show if modified from original)
                ui.mini.circular.givenNot(isModifiedFromOriginal) { disabled }.button {
                    onClick {
                        stopPlayback()
                        currentCode = props.code
                        editorRef { it.setCode(props.code) }
                    }
                    icon.undo()
                    +"Reset"
                }

                // Info text
                div {
                    css {
                        alignSelf = Align.center
                        fontSize = 0.85.rem
                        color = Color("#666")
                    }
                    if (isPlaying) {
                        icon.music()
                        +" Playing - Cycle $currentCycle"
                    } else {
                        +"Try this example"
                    }
                }
            }

            // Code editor
            CodeMirrorComp(
                code = currentCode,
                onCodeChanged = { newCode ->
                    currentCode = newCode
                }
            ).track(editorRef)
        }
    }
}
