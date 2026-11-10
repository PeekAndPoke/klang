/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.KlangCodePlaybackCtrl
import io.peekandpoke.klang.spine.spine
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.browser.window
import kotlinx.css.px
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.title

private val log = Logger(area = LogArea.Ui, source = "PlayableCodeExample")

@Suppress("FunctionName")
fun Tag.PlayableCodeExample(
    code: String,
    rpm: Double = 30.0,
    help: QuickHelpPanel? = null,
    onSignal: ((KlangPlaybackSignal?) -> Unit)? = null,
) = comp(
    PlayableCodeExample.Props(code = code, rpm = rpm, help = help, onSignal = onSignal)
) {
    PlayableCodeExample(it)
}

/**
 * A code example to play (KLANG-00370, the canvas's "The example player"): the song page's transport, small, over the
 * code on a small screen: Play (Update while it plays), Stop, the cycle, the RPM on a knob, and Revert
 * once the code was changed, and Copy. One example plays at a time. The highlights per sound event are the editor setting the
 * song page has (no EVT field of its own). Given [Props.help], Alt+Q and a hover show a name's help in the page's
 * bottom panel; without, the editor's own popup. [Props.onSignal] hears what the playback signals, for a picture that
 * follows it (KLANG-00371).
 */
class PlayableCodeExample(ctx: Ctx<Props>) : Component<PlayableCodeExample.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val rpm: Double = 30.0,
        /** The page's bottom panel for the code's help; null for the editor's own popup. */
        val help: QuickHelpPanel? = null,
        /** Each signal of the playback, as it comes: the notes scheduled, a stop, a live update. */
        val onSignal: ((KlangPlaybackSignal?) -> Unit)? = null,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val ctrl = spine.playbacks.ctrl()
        .code(props.code)
        .rpm(props.rpm)
        .preview()
        .build()

    private val editorCompRef = ComponentRef.Tracker<KlangCodeEditorComp>()

    private val state by subscribingTo(ctrl.state)

    private val highlightPerEvent by subscribingTo(EditorSettings.highlightsPerEvent) { newValue ->
        editorCompRef { it.setMaxHighlightsPerEvent(newValue) }
    }

    /** Each playback signal on to [Props.onSignal]; a plain subscription, as the example draws nothing from it. */
    private val passSignals = ctrl.signals.subscribeToStream { props.onSignal?.invoke(it) }

    private val isModifiedFromOriginal get() = state.code != props.code

    /** "Copied" on the copy key for a moment. */
    private var copied: Boolean by value(false)

    /** The copy key's way back to "Copy"; cleared when the example goes. */
    private var copiedTimer: Int? = null

    private var unmounted = false

    init {
        lifecycle {
            onUnmount {
                unmounted = true
                copiedTimer?.let { window.clearTimeout(it) }
                // Stopped first, so the stop still reaches whoever follows the playback; released, as the example goes
                ctrl.release()
                passSignals()
            }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "example"
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("margin", "8px 0")
                put("border-radius", "4px")
                put("background", "var(--kl-ground-deep)")
                put("border", "1px solid var(--kl-edge)")
            }

            renderTransport()

            div {
                key = "screen"
                css { put("padding", "0 8px 8px") }

                KlangScreen(size = KlangScreen.Size.Small, surface = "#000000") {
                    renderEditor()
                }
            }
        }
    }

    private fun DIV.renderTransport() {
        div {
            key = "transport"
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("align-items", "center")
                put("gap", "10px")
                put("padding", "8px 10px")
            }

            // The transport's controls on the equal grid, as on the song page; the keys after it on the right
            transportGrid {
                renderPlayButton()

                KlangButton(
                    onClick = { ctrl.stop() },
                    finish = KlangFinish.Silver,
                    surface = KlangButton.Surface.Flat,
                    shape = KlangButton.Shape.Pill,
                    availability = if (state.isPlaying) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
                ) {
                    title = "Stop playback"
                    icon.stop()
                    +"Stop"
                }

                transportCentred {
                    LcdDisplay(value = state.currentCycle, digits = 4, dim = !state.isPlaying)
                }

                transportSpanTwo {
                    KlangValueKnob(
                        name = "Revolutions per minute",
                        value = state.rpm,
                        range = KlangCodePlaybackCtrl.RPM_RANGE,
                        step = 0.5,
                        onChange = { ctrl.setRpm(it) },
                        unit = "RPM",
                        decimals = 2,
                        perPixel = 0.05,
                    )
                }
            }

            // The keys at the right, also when they wrap to a line of their own
            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "10px")
                    put("margin-left", "auto")
                }

                // Quiet, and only once there is something to go back from, as Reset was
                if (isModifiedFromOriginal) {
                    button(type = ButtonType.button, classes = "kl-quiet-key") {
                        onClick { backToOriginal() }

                        title = "Back to the original"
                        icon.undo()
                        +"Revert"
                    }
                }

                // What the editor holds, edits included (the maintainer, 2026-10-27: "please add a copy button here")
                button(type = ButtonType.button, classes = "kl-quiet-key") {
                    attributes["data-copy"] = ""
                    title = "Copy the code"
                    onClick { copy(state.code) }
                    // As wide as "Copied": its word changing moves nothing beside it
                    css {
                        put("min-width", "12ch")
                        put("text-align", "right")
                    }

                    icon.copy_outline()
                    +if (copied) "Copied" else "Copy"
                }
            }
        }
    }

    /** Copies [code]; "Copied" only once it is. Outside a secure context (a dev URL over http) there is no clipboard. */
    private fun copy(code: String) {
        val clipboard = window.navigator.asDynamic().clipboard ?: return

        clipboard.writeText(code).then(
            {
                if (!unmounted) {
                    copied = true
                    copiedTimer?.let { window.clearTimeout(it) }
                    copiedTimer = window.setTimeout({
                        copiedTimer = null
                        copied = false
                    }, 2000)
                }
            },
            { e: dynamic -> log.warn("Could not copy the code", e) },
        )
    }

    /** Play, or update while it plays once its code changed; retry when the engine did not start. */
    private fun DIV.renderPlayButton() {
        val updating = state.isPlaying
        val enabled = !updating || state.isCodeModified

        KlangButton(
            onClick = { ctrl.play() },
            finish = KlangFinish.Silver,
            surface = KlangButton.Surface.Flat,
            shape = KlangButton.Shape.Pill,
            availability = if (enabled) KlangButton.Availability.Enabled else KlangButton.Availability.Disabled,
        ) {
            when {
                updating -> {
                    icon.redo_alternate()
                    +"Update"
                }

                // Clicking retries: Player.ensure() drops its memo on failure
                state.isPlayerFailed -> {
                    icon.exclamation_triangle()
                    +"Retry"
                }

                state.isPlayerLoading -> {
                    KlangSpinner(label = "Loading the Motor", size = 14.px)
                    +"Loading"
                }

                else -> {
                    icon.play()
                    +"Play"
                }
            }
        }
    }

    private fun DIV.renderEditor() {
        val help = props.help

        KlangCodeEditorComp(
            ctrl = ctrl,
            availableLibraries = listOf(stdlibLib, sprudelLib),
            autoImportedLibraries = listOf(stdlibLib, sprudelLib),
            maxHighlightsPerEvent = highlightPerEvent,
            onQuickDocumentation = help?.let { panel ->
                { symbol, declarationCode, usages ->
                    panel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), jumpTo = ::jumpTo)
                }
            },
            onHoverDocumentation = help?.let { panel ->
                { symbol, declarationCode, usages ->
                    panel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), jumpTo = ::jumpTo)
                }
            },
        ).track(editorCompRef)
    }

    private fun jumpTo(pos: Int) {
        editorCompRef { it.jumpTo(pos) }
    }

    private fun backToOriginal() {
        ctrl.stop()
        ctrl.setCode(props.code)
        editorCompRef { it.setCode(props.code) }
    }
}
