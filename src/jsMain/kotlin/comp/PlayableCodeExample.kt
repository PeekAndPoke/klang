/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.script.stdlibLib
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
import kotlinx.css.px
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.PlayableCodeExample(
    code: String,
    rpm: Double = 30.0,
    help: QuickHelpPanel? = null,
) = comp(
    PlayableCodeExample.Props(code = code, rpm = rpm, help = help)
) {
    PlayableCodeExample(it)
}

/**
 * A code example to play (KLANG-00370, the canvas's "The example player"): the song page's transport, small, over the
 * code on a small screen: Play (Update while it plays), Stop, the cycle, the RPM on a knob, and "Back to the original"
 * once the code was changed. One example plays at a time. The highlights per sound event are the editor setting the
 * song page has (no EVT field of its own). Given [Props.help], Alt+Q and a hover show a name's help in the page's
 * bottom panel; without, the editor's own popup.
 */
class PlayableCodeExample(ctx: Ctx<Props>) : Component<PlayableCodeExample.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val rpm: Double = 30.0,
        /** The page's bottom panel for the code's help; null for the editor's own popup. */
        val help: QuickHelpPanel? = null,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val ctrl = KlangCodePlaybackCtrl.builder()
        .code(props.code)
        .rpm(props.rpm)
        .exclusive()
        .build()

    private val editorCompRef = ComponentRef.Tracker<KlangCodeEditorComp>()

    private val state by subscribingTo(ctrl.state)

    private val highlightPerEvent by subscribingTo(EditorSettings.highlightsPerEvent) { newValue ->
        editorCompRef { it.setMaxHighlightsPerEvent(newValue) }
    }

    private val isModifiedFromOriginal get() = state.code != props.code

    init {
        lifecycle {
            onUnmount { ctrl.stop() }
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

            LcdDisplay(value = state.currentCycle, digits = 4, dim = !state.isPlaying)

            KlangValueKnob(
                name = "Revolutions per minute",
                value = state.rpm,
                range = 10.0..120.0,
                step = 0.5,
                onChange = { ctrl.setRpm(it) },
                unit = "RPM",
                decimals = 2,
                perPixel = 0.05,
            )

            div { css { put("flex", "1") } }

            // Quiet, and only once there is something to go back from, as Reset was
            if (isModifiedFromOriginal) {
                button(type = ButtonType.button, classes = "kl-quiet-key") {
                    onClick { backToOriginal() }

                    icon.undo()
                    +"Back to the original"
                }
            }
        }
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
                    panel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), byKey = true, jumpTo = ::jumpTo)
                }
            },
            onHoverDocumentation = help?.let { panel ->
                { symbol, declarationCode, usages ->
                    panel.showHelp(entry = HelpWindow.Entry(symbol, declarationCode, usages), byKey = false, jumpTo = ::jumpTo)
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
