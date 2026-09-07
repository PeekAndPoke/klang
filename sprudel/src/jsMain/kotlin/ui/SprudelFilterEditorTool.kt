/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgPath
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
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// ── Filter curve shapes ──────────────────────────────────────────────────────

enum class FilterCurveShape {
    LowPass,
    HighPass,
    BandPass,
    Notch,
}

// ── Configurable tool class ──────────────────────────────────────────────────

/**
 * Configurable [KlangUiToolEmbeddable] for the per-param filter functions (freq, q).
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits freq AND q of the host
 *   call and commits the full argument list.
 * - Scalar fallback (embedded / sequence atom): edits a single freq value.
 *
 * The envelope depth is the `env` slot of the filter call (2026-09-07); this tool edits freq and q only.
 */
class SprudelFilterEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
    val curveShape: FilterCurveShape,
    val freqLabel: String = "Cutoff",
    val resLabel: String = "Resonance (Q)",
) : KlangUiToolEmbeddable {

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelFilterEditorComp(ctx, embedded = false, tool = this@SprudelFilterEditorTool)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelFilterEditorComp(ctx, embedded = true, tool = this@SprudelFilterEditorTool)
    }
}

// ── Singleton instances ──────────────────────────────────────────────────────

object SprudelLpFilterEditorTool : KlangUiToolEmbeddable by SprudelFilterEditorTool(
    title = "Low Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.LowPass,
    freqLabel = "Cutoff",
    resLabel = "Resonance",
)

object SprudelHpFilterEditorTool : KlangUiToolEmbeddable by SprudelFilterEditorTool(
    title = "High Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.HighPass,
    freqLabel = "Cutoff",
    resLabel = "Resonance",
)

object SprudelBpFilterEditorTool : KlangUiToolEmbeddable by SprudelFilterEditorTool(
    title = "Band Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.BandPass,
    freqLabel = "Frequency",
    resLabel = "Q",
)

object SprudelNotchFilterEditorTool : KlangUiToolEmbeddable by SprudelFilterEditorTool(
    title = "Notch Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.Notch,
    freqLabel = "Frequency",
    resLabel = "Q",
)

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelFilterEditorComp(
    toolCtx: KlangUiToolContext,
    embedded: Boolean,
    tool: SprudelFilterEditorTool,
) = comp(
    SprudelFilterEditorComp.Props(toolCtx, embedded, tool)
) {
    SprudelFilterEditorComp(it)
}

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelFilterEditorComp(ctx: Ctx<Props>) : Component<SprudelFilterEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val embedded: Boolean = false,
        val tool: SprudelFilterEditorTool,
    )

    // ── Parse current value from raw source text ─────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val call = props.toolCtx.call

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseNum(text: String?, fallback: Double): Double =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull() ?: fallback

    private fun parseNumOrNull(text: String?): Double? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull()

    // Whole-call mode reads freq/q from the host call's args; scalar mode reads the single arg.
    private val parsedFreq
        get() = parseNum(call?.args?.getOrNull(0) ?: currentValue, 2000.0)

    // NOTE: 1.0 mirrors today's sprudel-side default; C1 unifies the default q to 0.707.
    private val parsedQ
        get() = parseNum(call?.args?.getOrNull(1), 1.0)

    private var freq by value(parsedFreq)
    private var resonance by value(parsedQ)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = List(2) { parseNumOrNull(call?.args?.getOrNull(it)) != null }
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) "${freq.fmt()}, ${resonance.fmt()}" else freq.fmt()

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
            while (texts.size < 2) texts.add(null)
            put(texts, 0, freq.fmt())
            put(texts, 1, resonance.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(freq.fmt())
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
        get() = if (call != null) buildValue() != lastCommitted else currentValue != buildValue()

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
        currentValue = initialValue
        freq = parsedFreq
        resonance = parsedQ
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        commitValue()
    }

    // ── Render ───────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "filter-editor"
                css { minWidth = 500.px }
                toolHeaderWithInfo(props.tool.title, props.toolCtx, infoPopup)
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
            key = "filter-editor-content-$resetCounter"

            ui.form {
                key = "filter-editor-form"
                ui.two.stackable.fields {
                    key = "filter-editor-fields"
                    UiInputField(freq, { freq = it; dirty += 0; liveUpdate() }) {
                        domKey("freq")
                        step(10.0)
                        label(props.tool.freqLabel)
                        rightLabel { ui.basic.label { +"Hz" } }
                    }
                    if (call != null) {
                        UiInputField(resonance, { resonance = it; dirty += 1; liveUpdate() }) {
                            domKey("q")
                            step(0.1)
                            label(props.tool.resLabel)
                        }
                    }
                }
            }
            ui.divider {
                key = "filter-editor-divider"
            }
            div {
                key = "filter-editor-curve"
                css { if (!props.embedded) marginBottom = 1.rem }
                renderFilterSvg()
            }
        }
    }

    // ── SVG curve ────────────────────────────────────────────────────────────

    private fun FlowContent.renderFilterSvg() {
        val w = 500.0
        val h = 100.0
        val padL = 20.0
        val padR = 20.0
        val padT = 10.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val logMin = ln(20.0)
        val logMax = ln(20000.0)

        val safeFreq = freq.coerceAtLeast(1.0)
        val safeQ = resonance.coerceAtLeast(0.1)

        // Response function based on filter type
        fun response(f: Double): Double {
            val ratio = f / safeFreq
            return when (props.tool.curveShape) {
                FilterCurveShape.LowPass -> {
                    1.0 / sqrt(1.0 + ratio.pow(4.0))
                }

                FilterCurveShape.HighPass -> {
                    val r2 = ratio * ratio
                    r2 / sqrt(r2 * r2 + 1.0 / (safeQ * safeQ) * r2 + 1.0)
                }

                FilterCurveShape.BandPass -> {
                    val r = ratio
                    val bw = 1.0 / safeQ
                    (bw * r) / sqrt((r * r - 1.0).pow(2.0) + (bw * r).pow(2.0))
                }

                FilterCurveShape.Notch -> {
                    val r2 = ratio * ratio
                    val num = (r2 - 1.0).pow(2.0)
                    val den = (r2 - 1.0).pow(2.0) + (ratio / safeQ).pow(2.0)
                    sqrt(num / den.coerceAtLeast(1e-10))
                }
            }
        }

        // Build polyline points
        val step = 2
        val points = buildString {
            var x = padL.toInt()
            var first = true
            while (x <= (padL + drawW).toInt()) {
                val f = exp(logMin + (x - padL) / drawW * (logMax - logMin))
                val y = padT + drawH * (1.0 - response(f).coerceIn(0.0, 1.0))
                if (!first) append(" ")
                append("$x,$y")
                first = false
                x += step
            }
        }

        // Fill path
        val fillPath = buildString {
            append("M$padL ${padT + drawH} ")
            var x = padL.toInt()
            while (x <= (padL + drawW).toInt()) {
                val f = exp(logMin + (x - padL) / drawW * (logMax - logMin))
                val y = padT + drawH * (1.0 - response(f).coerceIn(0.0, 1.0))
                append("L$x $y ")
                x += step
            }
            append("L${padL + drawW} ${padT + drawH} Z")
        }

        // Frequency label positions on log scale
        fun freqX(freq: Double) = padL + (ln(freq) - logMin) / (logMax - logMin) * drawW

        val x100 = freqX(100.0)
        val x1k = freqX(1000.0)
        val x10k = freqX(10000.0)

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            // Grid lines
            svgLine(x100, padT, x100, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x1k, padT, x1k, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x10k, padT, x10k, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // Fill under curve
            svgPath(d = fillPath, fill = "${laf.gold}26")
            // Curve line
            svgPath(d = "M${points.replace(" ", "L").replace(",", " ")}", stroke = laf.gold, strokeWidth = "1")
            // Frequency labels
            svgText(x100, h - 3, "100", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(x1k, h - 3, "1k", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(x10k, h - 3, "10k", fill = "#ccc", fontSize = "7", textAnchor = "middle")
        }
    }
}
