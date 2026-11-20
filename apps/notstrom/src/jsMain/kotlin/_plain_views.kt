/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.BrowserCheck
import io.peekandpoke.klang.apps.common.FrameStats
import io.peekandpoke.klang.apps.common.MachineScore
import io.peekandpoke.klang.audio_engine.KlangBenchmark
import io.peekandpoke.klang.viz.liveact.Picture
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onChange
import io.peekandpoke.ultra.html.onClick
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import org.w3c.dom.HTMLInputElement
import kotlin.math.min
import kotlin.math.roundToInt

// Notstrom's few views, in its plain look: light text on the dark page, flat buttons, three ranks on a screen (its
// leading line, the body, the action). No library of looks, no boxes and no rules.

/** A screen's leading line: the song's line on the start screen, a test's name, the result's. */
private const val LEAD_SIZE = "clamp(22px, 1.6vw, 34px)"

/** The gap from the leading line to the body, and between the body's parts. */
private const val BODY_GAP = "18px"

/** The gap above the action (the screen's button). */
private const val ACTION_GAP = "44px"

/**
 * A screen before the show, over the whole page: the band's mark at the top ([mark], placed by the page's size, the
 * same on every screen, so nothing jumps between them), and [content] below it, its middle at 60 percent of the
 * height: a grid keeps the mark's band (and a little air) free above, and below it a band that puts the middle row's
 * centre there. Content taller than the room starts right under the mark ("safe" centring) and runs on down.
 * [marked] false leaves the mark out, and the content where it stands with it (the quiet line on the black while the
 * show gets ready).
 */
fun FlowContent.notstromScreen(marked: Boolean = true, content: FlowContent.() -> Unit) {
    val top = "calc(${markBottom()} + 4vh)"

    div {
        css {
            put("position", "absolute")
            put("inset", "0")
            put("z-index", "10")
            put("background", "#020308")
            put("display", "grid")
            // the middle row's centre at 60vh: (top + 100vh - bottom) / 2 = 60vh
            put("grid-template-rows", "$top 1fr max(0px, calc($top - 20vh))")
            put("overflow", "hidden")
        }

        if (marked) {
            mark()
        }

        div {
            css {
                put("grid-row", "2")
                put("align-self", "safe center")
                put("justify-self", "center")
                put("display", "flex")
                put("flex-direction", "column")
                put("align-items", "center")
                put("padding", "0 16px")
                put("text-align", "center")
            }

            content()
        }
    }
}

/**
 * [content] (a screen) seen by [share], from 0 to 1, while the show's opening fades it ([OpeningFade]); it takes no
 * clicks then. Nothing at all where nothing of it is seen.
 */
fun FlowContent.faded(share: Double, content: FlowContent.() -> Unit) {
    if (share <= 0.0) {
        return
    }

    div {
        css {
            put("position", "absolute")
            put("inset", "0")
            put("z-index", "10")
            put("opacity", "$share")
            put("pointer-events", "none")
        }

        content()
    }
}

/**
 * The black over the stage while the show's opening fades it ([OpeningFade]), seen by [share], from 0 to 1. The stage
 * under it keeps its size and is really drawn; the black takes no clicks.
 */
fun FlowContent.blackLayer(share: Double) {
    if (share <= 0.0) {
        return
    }

    div {
        css {
            put("position", "absolute")
            put("inset", "0")
            put("background", "#020308")
            put("opacity", "$share")
            put("pointer-events", "none")
        }
    }
}

/** The leading line of a screen. */
fun FlowContent.leadLine(text: String) {
    div {
        css {
            put("font-size", LEAD_SIZE)
            put("letter-spacing", "0.04em")
            put("color", "#c8d0de")
        }
        +text
    }
}

/** Body text, under the leading line or a part before; [color] where it stands out (a result, a problem). */
fun FlowContent.bodyText(color: String = "#8a93a8", content: FlowContent.() -> Unit) {
    div {
        css {
            put("margin-top", BODY_GAP)
            put("max-width", "36em")
            put("font-size", "16px")
            put("color", color)
        }

        content()
    }
}

/** A screen's action: a clear button to press ([primary] light on dark, else outlined). */
fun FlowContent.actionButton(text: String, primary: Boolean = true, disabled: Boolean = false, onClick: () -> Unit) {
    button {
        this.disabled = disabled
        css {
            put("min-width", "180px")
            put("padding", "16px 44px")
            put("font", "inherit")
            put("font-size", "18px")
            put("border-radius", "6px")
            put("cursor", if (disabled) "default" else "pointer")

            if (primary) {
                put("color", "#020308")
                put("background", if (disabled) "#6b7385" else "#e8ecf4")
                put("border", "0")
            } else {
                put("color", "#e8ecf4")
                put("background", "transparent")
                put("border", "1px solid #6b7385")
            }
        }
        onClick { onClick() }

        +text
    }
}

/** The screen's actions, side by side, [ACTION_GAP] under what stands above them ([gap] where that is closer). */
fun FlowContent.actions(gap: String = ACTION_GAP, content: FlowContent.() -> Unit) {
    div {
        css {
            put("margin-top", gap)
            put("display", "flex")
            put("justify-content", "center")
            put("gap", "16px")
        }

        content()
    }
}

/** A small flat button, for the drawer: [primary] light on dark, else outlined. */
fun FlowContent.plainButton(text: String, primary: Boolean = true, disabled: Boolean = false, onClick: () -> Unit) {
    button {
        this.disabled = disabled
        css {
            put("padding", "8px 20px")
            put("font", "inherit")
            put("font-size", "15px")
            put("border-radius", "6px")
            put("cursor", if (disabled) "default" else "pointer")

            if (primary) {
                put("color", "#020308")
                put("background", if (disabled) "#6b7385" else "#e8ecf4")
                put("border", "0")
            } else {
                put("color", "#e8ecf4")
                put("background", "transparent")
                put("border", "1px solid #6b7385")
            }
        }
        onClick { onClick() }

        +text
    }
}

/** The studio's warning outside desktop Chrome ([BrowserCheck]), with its two answers. */
fun FlowContent.browserWarning(asked: BrowserCheck.Warning, onContinue: () -> Unit, onNotNow: () -> Unit) {
    leadLine(asked.headline)
    bodyText { +asked.text }

    actions {
        actionButton(text = BrowserCheck.CONTINUE, onClick = onContinue)
        actionButton(text = BrowserCheck.NOT_NOW, primary = false, onClick = onNotNow)
    }
}

/** The sound test as it runs: the round and the voices the engine renders, climbing, with a bar to the full mark. */
fun FlowContent.soundTest(progress: KlangBenchmark.Progress?) {
    val voices = progress?.activeVoices ?: 0
    val round = progress?.let { min(it.currentIteration + 1, it.totalIterations) } ?: 1
    val rounds = progress?.totalIterations ?: 1

    leadLine("Checking the sound")
    bodyText { +"Round $round of $rounds: $voices PS (the engine's power)" }
    progressBar(share = voices.toDouble() / MachineScore.FULL_VOICES)
    bodyText { +"How many voices this machine's engine renders in time." }
}

/** The picture test as it runs, [tenths] of it done (0 to 10): the stage is drawn behind this screen, not seen. */
fun FlowContent.pictureTest(tenths: Int) {
    leadLine("Checking the picture")
    bodyText { +"How fast this machine draws the stage, for a few seconds." }
    progressBar(share = tenths / 10.0)
}

/** The band's figures as they load: how many of how many, with a bar. */
fun FlowContent.figuresLoading(figures: Picture.Figures) {
    leadLine("Getting the band ready")
    bodyText { +"The band's figures: ${figures.arrived} of ${figures.of}" }
    progressBar(share = if (figures.of > 0) figures.arrived.toDouble() / figures.of else 0.0)
}

/** A thin bar, filled to [share] (0 to 1). */
fun FlowContent.progressBar(share: Double) {
    div {
        css {
            put("margin-top", BODY_GAP)
            put("width", "16em")
            put("height", "6px")
            put("border-radius", "3px")
            put("background", "#1b2030")
            put("overflow", "hidden")
        }

        div {
            css {
                put("height", "100%")
                put("width", "${(100.0 * share.coerceIn(0.0, 1.0)).roundToInt()}%")
                put("background", "#9aa6bc")
            }
        }
    }
}

/**
 * The choices made right before the show starts, one under the other, in the body's size and close above the button
 * that starts it: without the stroboscope, and fullscreen. [strobe] true: the stroboscope flashes; [fullscreen] true:
 * the show starts in fullscreen.
 */
fun FlowContent.showOptions(strobe: Boolean, fullscreen: Boolean, onStrobe: (Boolean) -> Unit, onFullscreen: (Boolean) -> Unit) {
    div {
        css {
            put("margin-top", "28px")
            put("display", "flex")
            put("flex-direction", "column")
            put("align-items", "flex-start")
            put("gap", "8px")
        }

        plainCheckbox(text = "Without the stroboscope (its fast flashing lights)", checked = !strobe) { onStrobe(!it) }
        plainCheckbox(text = "Use fullscreen", checked = fullscreen, onChange = onFullscreen)
    }
}

/** A checkbox with its [text], told of each change. */
fun FlowContent.plainCheckbox(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    label {
        css {
            put("display", "flex")
            put("align-items", "center")
            put("gap", "8px")
            put("color", "#8a93a8")
            put("font-size", "16px")
            put("cursor", "pointer")
        }

        input(type = InputType.checkBox) {
            this.checked = checked
            onChange { event -> onChange((event.target as? HTMLInputElement)?.checked == true) }
        }

        +text
    }
}

/**
 * The tests' result: the three numbers, the sentence, the picture's quality chosen ([quality]), when it was measured
 * where it is a kept result ([measured]; null: none), the choices made before the show ([options]), and the button
 * that starts it; beside it, where [onTestAgain] is given, a quiet one that runs the tests again.
 */
fun FlowContent.testResult(
    voices: Int,
    frames: FrameStats,
    score: MachineScore,
    quality: String,
    measured: String? = null,
    options: FlowContent.() -> Unit,
    onStart: () -> Unit,
    onTestAgain: (() -> Unit)? = null,
) {
    leadLine("This machine")

    bodyText {
        resultLine(label = "Sound", value = score.sound.roundToInt(), detail = "$voices PS (the engine's power)")
        resultLine(
            label = "Picture",
            value = score.picture.roundToInt(),
            detail = "${frames.fps.roundToInt()} frames a second, slowest ${frames.slowestMs.roundToInt()} ms",
        )
        resultLine(label = "Score", value = score.combined, detail = "of 100")
    }

    bodyText(color = "#c8d0de") { +score.verdict }

    bodyText { +quality }

    measured?.let { line ->
        bodyText(color = "#6b7385") { +line }
    }

    options()

    actions(gap = "28px") {
        actionButton(text = "Start the show", onClick = onStart)

        onTestAgain?.let { again ->
            actionButton(text = TEST_AGAIN, primary = false, onClick = again)
        }
    }
}

/** The quiet button beside a kept result's start: both tests run again, and their result is kept instead. */
const val TEST_AGAIN = "Run the machine test again"

private fun FlowContent.resultLine(label: String, value: Int, detail: String) {
    div {
        css {
            put("display", "flex")
            put("gap", "12px")
            put("justify-content", "center")
            put("align-items", "baseline")
            put("font-variant-numeric", "tabular-nums")
        }

        div {
            css {
                put("width", "5em")
                put("text-align", "right")
            }
            +label
        }

        div {
            css {
                put("width", "2.5em")
                put("font-size", "20px")
                put("color", "#e8ecf4")
            }
            +"$value"
        }

        div {
            css {
                put("width", "22em")
                put("text-align", "left")
            }
            +detail
        }
    }
}
