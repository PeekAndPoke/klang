/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgPolyline
import io.peekandpoke.klang.ui.svgRect
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.klang.ui.svgText
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.forms.formController
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.gap
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.label
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * [KlangUiToolEmbeddable] for the per-param distort(amount, shape, oversample) call.
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits amount plus the optional
 *   shape/oversample params of the host call and commits the full argument list. The shape is a
 *   STRING param and commits as a quoted string literal; unset optionals stay omitted (null slots).
 * - Scalar fallback (embedded / sequence atom): edits a single amount value.
 */
object SprudelDistortEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Distort Editor"

    override val iconFn: SemanticIconFn = { bolt }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelDistortEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelDistortEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelDistortEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelDistortEditorComp.Props(toolCtx, embedded)) { SprudelDistortEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelDistortEditorComp(ctx: Ctx<Props>) : Component<SprudelDistortEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    companion object {
        val shapes = listOf(
            "soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp",
            "softsat", "tube", "linearfold", "zerosquare", "sineshaper", "asym", "stompbox",
        )
    }

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val call = props.toolCtx.call

    private val initialValue = props.toolCtx.currentValue ?: ""

    private fun parseNum(text: String?, fallback: Double): Double =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull() ?: fallback

    private fun parseNumOrNull(text: String?): Double? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull()

    private fun parseStr(text: String?): String? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")

    // Whole-call mode reads the params from the host call's args; scalar mode reads the single arg.
    private val parsedAmount
        get() = parseNum(call?.args?.getOrNull(0) ?: initialValue, 0.5)

    private val parsedShape
        get() = parseStr(call?.args?.getOrNull(1))?.takeIf { it in shapes }

    private val parsedOversample
        get() = parseStr(call?.args?.getOrNull(2))?.toIntOrNull()

    private var amount by value(parsedAmount)
    private var shape by value(parsedShape)
    private var oversample by value(parsedOversample)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = listOf(
        parseNumOrNull(call?.args?.getOrNull(0)) != null,
        parseStr(call?.args?.getOrNull(1))?.let { it in shapes } == true,
        parseStr(call?.args?.getOrNull(2))?.toIntOrNull() != null,
    )
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "${amount.fmt()}, ${shape ?: "-"}, ${oversample?.takeIf { it > 1 }?.toString() ?: "-"}"
        } else {
            amount.fmt()
        }

    /**
     * Writes a slot only when that is safe: the user touched it, or the original arg parses
     * (rewriting it loses nothing). Untouched non-parseable args are preserved; untouched
     * absent slots stay absent so the engine defaults apply.
     */
    private fun put(texts: MutableList<String?>, index: Int, text: String?) {
        val original = call?.args?.getOrNull(index)
        if (index in dirty || (original != null && parseable[index])) {
            texts[index] = text
        }
    }

    private fun commitValue() {
        val c = call
        if (c != null) {
            val texts = c.args.toMutableList()
            while (texts.size < 3) texts.add(null)
            put(texts, 0, amount.fmt())
            // shape is a STRING param — commits as a quoted string literal; unset = null slot
            put(texts, 1, shape?.let { "\"$it\"" })
            // "Off" / 1x is the engine default — omit the arg
            put(texts, 2, oversample?.takeIf { it > 1 }?.toString())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(amount.fmt())
        }
        hasCommitted = true
        lastCommitted = buildValue()
    }

    // Built-state fingerprints: in whole-call mode [initialValue] is only the clicked arg's
    // text, so the Reset/Update buttons compare built snapshots instead (initial state and
    // last committed state); scalar mode keeps the plain text comparison.
    private val initialBuiltValue = buildValue()
    private var lastCommitted = initialBuiltValue

    private val isInitialModified
        get() = if (call != null) buildValue() != initialBuiltValue else initialValue != buildValue()

    private val isCurrentModified
        get() = if (call != null) buildValue() != lastCommitted else (props.toolCtx.currentValue ?: "") != buildValue()

    private fun liveUpdate() {
        if (props.embedded || autoUpdate) {
            commitValue()
        }
    }

    private fun onCancel() {
        if (!props.embedded && autoUpdate && hasCommitted && isInitialModified) {
            val c = call
            if (c != null) c.onCommitCall(c.args) else props.toolCtx.onCommit(initialValue)
        }
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        dirty.clear()
        amount = parsedAmount
        shape = parsedShape
        oversample = parsedOversample
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        commitValue()
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                toolHeaderWithInfo("Distort", props.toolCtx, infoPopup)
                renderContent()
                ui.divider {}
                ToolButtonBar(
                    isInitialModified = isInitialModified,
                    isCurrentModified = isCurrentModified,
                    onCancel = ::onCancel,
                    onReset = ::onReset,
                    onCommit = ::onCommit,
                )
            }
        }
    }

    private fun FlowContent.renderContent() {
        div {
            key = "distort-editor-content-$resetCounter"

            ui.form {
                ui.stackable.fields {
                    UiInputField(amount, { amount = it; dirty += 0; liveUpdate() }) {
                        domKey("amount")
                        step(0.01)
                        appear { three.wide }
                        label {
                            +"Amount"
                            paramInfoIcon("amount", props.toolCtx, infoPopup)
                        }
                    }

                    // Oversampling buttons (whole-call mode only)
                    if (call != null) {
                        noui.field {
                            label {
                                +"Oversampling"
                                paramInfoIcon("oversample", props.toolCtx, infoPopup)
                            }
                            div {
                                css {
                                    display = Display.flex
                                    flexWrap = FlexWrap.wrap
                                    gap = 6.px
                                    marginTop = 6.px
                                }
                                for ((label, factor) in listOf("Off" to null, "2x" to 2, "4x" to 4, "8x" to 8)) {
                                    val isSelected = oversample == factor
                                    ui.small.givenNot(isSelected) { basic }
                                        .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                            key = "os-${factor ?: "off"}"
                                            onClick { oversample = factor; dirty += 2; liveUpdate() }
                                            +label
                                        }
                                }
                            }
                        }
                    }
                }

                // Shape buttons (whole-call mode only)
                if (call != null) {
                    noui.field {
                        label {
                            +"Shape"
                            paramInfoIcon("shape", props.toolCtx, infoPopup)
                        }

                        div {
                            css {
                                display = Display.flex
                                flexWrap = FlexWrap.wrap
                                gap = 6.px
                                marginTop = 6.px
                            }
                            // "default" button — clears shape selection
                            val isDefault = shape == null
                            ui.small.givenNot(isDefault) { basic }
                                .given(isDefault) { with(laf.styles.goldButton()) }.button {
                                    key = "default"
                                    onClick { shape = null; dirty += 1; liveUpdate() }
                                    +"default"
                                }
                            for (s in shapes) {
                                val isSelected = shape == s
                                ui.small.givenNot(isSelected) { basic }
                                    .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                        key = s
                                        onClick { shape = s; dirty += 1; liveUpdate() }
                                        +s
                                    }
                            }
                        }
                    }
                }
            }

            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderTransferCurve()
            }
        }
    }

    // ── SVG visualization: waveshaper transfer curve ─────────────────────────

    private fun FlowContent.renderTransferCurve() {
        val w = 400.0
        val h = 120.0
        val padL = 22.0
        val padR = 6.0
        val padT = 6.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB
        val midY = padT + drawH / 2.0

        val clampedAmount = amount.coerceIn(0.0, 2.0)
        val goldHex = laf.gold
        val currentShape = shape ?: "soft"

        val numPoints = drawW.toInt()
        val points = buildString {
            for (i in 0 until numPoints) {
                val x = -1.0 + 2.0 * i / numPoints  // input: -1..1
                val driven = x * (1.0 + clampedAmount * 4.0)
                val y = waveshape(driven, currentShape)
                val px = padL + i.toDouble()
                val py = midY - y * (drawH / 2.0 * 0.9)
                if (i > 0) append(" ")
                append("$px,$py")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            // Center lines
            svgLine(padL, midY, padL + drawW, midY, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(
                padL + drawW / 2, padT, padL + drawW / 2, padT + drawH,
                stroke = "rgba(255,255,255,0.1)", strokeWidth = "0.5",
            )
            // Diagonal (linear reference)
            svgLine(padL, padT + drawH, padL + drawW, padT, stroke = "rgba(255,255,255,0.08)", strokeWidth = "0.5")
            // Transfer curve
            svgPolyline(
                points = points,
                stroke = goldHex,
                strokeWidth = "1.5",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )
            // Axis labels
            svgText(padL - 3, midY + 2, "0", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL - 3, padT + 4, "1", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL - 3, padT + drawH, "-1", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL + drawW / 2, h - 4, "Input", fill = "#ccc", fontSize = "5", textAnchor = "middle")
            svgText(
                x = 4, y = midY,
                text = "Output", fill = "#ccc", fontSize = "5", textAnchor = "middle",
                transform = "rotate(-90, 4, $midY)",
            )
        }
    }

    private fun waveshape(x: Double, shape: String): Double = when (shape) {
        "soft" -> tanh(x)
        "hard" -> x.coerceIn(-1.0, 1.0)
        "gentle" -> x / (1.0 + abs(x))
        "cubic" -> {
            val c = x.coerceIn(-1.0, 1.0)
            c - c * c * c / 3.0
        }

        "diode" -> if (x >= 0.0) tanh(x) else tanh(x * 0.5)
        "fold" -> sin(x * PI / 2.0)
        "chebyshev" -> {
            val c = x.coerceIn(-1.0, 1.0)
            4.0 * c * c * c - 3.0 * c
        }

        "rectify" -> abs(tanh(x))
        "exp" -> sign(x) * (1.0 - exp(-abs(x)))

        "softsat" -> x / sqrt(1.0 + x * x)
        "tube" -> (tanh(x + 0.5) - 0.46211715726000974) * 0.6839397205857212
        "linearfold" -> {
            val shifted = x + 1.0
            val phase = shifted - 4.0 * floor(shifted * 0.25)
            1.0 - abs(phase - 2.0)
        }

        "zerosquare" -> tanh(x * 8.0)
        "sineshaper" -> sin(x * PI * 0.5)
        "asym" -> if (x >= 0.0) {
            val xc = if (x > 1.0) 1.0 else x
            1.5 * xc - 0.5 * xc * xc * xc
        } else {
            val xc = if (x < -1.0) 1.0 else -x
            -sqrt(xc)
        }

        "stompbox" -> if (x >= 0.0) 1.0 - exp(-x * 1.5) else -(1.0 - exp(x * 3.0))
        else -> tanh(x)
    }

    private fun tanh(x: Double): Double {
        val e2x = exp(2.0 * x)
        return (e2x - 1.0) / (e2x + 1.0)
    }
}
