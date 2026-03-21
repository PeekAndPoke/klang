package io.peekandpoke.klang.sprudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.forms.formController
import de.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.*
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
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

/** Configurable [KlangUiToolEmbeddable] for editing combined filter strings (freq:resonance:env). */
class StrudelFilterEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
    val curveShape: FilterCurveShape,
    val freqLabel: String = "Cutoff",
    val resLabel: String = "Resonance (Q)",
) : KlangUiToolEmbeddable {

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelFilterEditorComp(ctx, embedded = false, tool = this@StrudelFilterEditorTool)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelFilterEditorComp(ctx, embedded = true, tool = this@StrudelFilterEditorTool)
    }
}

// ── Singleton instances ──────────────────────────────────────────────────────

object StrudelLpFilterEditorTool : KlangUiToolEmbeddable by StrudelFilterEditorTool(
    title = "Low Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.LowPass,
    freqLabel = "Cutoff",
    resLabel = "Resonance",
)

object StrudelHpFilterEditorTool : KlangUiToolEmbeddable by StrudelFilterEditorTool(
    title = "High Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.HighPass,
    freqLabel = "Cutoff",
    resLabel = "Resonance",
)

object StrudelBpFilterEditorTool : KlangUiToolEmbeddable by StrudelFilterEditorTool(
    title = "Band Pass Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.BandPass,
    freqLabel = "Frequency",
    resLabel = "Q",
)

object StrudelNotchFilterEditorTool : KlangUiToolEmbeddable by StrudelFilterEditorTool(
    title = "Notch Filter",
    iconFn = { filter },
    curveShape = FilterCurveShape.Notch,
    freqLabel = "Frequency",
    resLabel = "Q",
)

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelFilterEditorComp(
    toolCtx: KlangUiToolContext,
    embedded: Boolean,
    tool: StrudelFilterEditorTool,
) = comp(
    StrudelFilterEditorComp.Props(toolCtx, embedded, tool)
) {
    StrudelFilterEditorComp(it)
}

// ── Component ────────────────────────────────────────────────────────────────

private class StrudelFilterEditorComp(ctx: Ctx<Props>) : Component<StrudelFilterEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val embedded: Boolean = false,
        val tool: StrudelFilterEditorTool,
    )

    // ── Parse current value from raw source text ─────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private val parsed
        get() = run {
            val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
            val parts = raw.split(":").map { it.toDoubleOrNull() }
            Triple(
                parts.getOrNull(0) ?: 2000.0,   // freq Hz
                parts.getOrNull(1) ?: 1.0,       // resonance / Q
                parts.getOrNull(2) ?: 0.0,       // env depth
            )
        }

    private var freq by value(parsed.first)
    private var resonance by value(parsed.second)
    private var envDepth by value(parsed.third)

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${freq.fmt()}:${resonance.fmt()}:${envDepth.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    private fun liveUpdate() {
        if (props.embedded || autoUpdate) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() {
        if (!props.embedded && autoUpdate && isInitialModified) {
            props.toolCtx.onCommit(initialValue)
        }
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        currentValue = initialValue
        freq = parsed.first
        resonance = parsed.second
        envDepth = parsed.third
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
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
                ui.three.stackable.fields {
                    key = "filter-editor-fields"
                    UiInputField(freq, { freq = it; liveUpdate() }) {
                        domKey("freq")
                        step(10.0)
                        label(props.tool.freqLabel)
                        rightLabel { ui.basic.label { +"Hz" } }
                    }
                    UiInputField(resonance, { resonance = it; liveUpdate() }) {
                        domKey("resonance")
                        step(0.1)
                        label(props.tool.resLabel)
                    }
                    UiInputField(envDepth, { envDepth = it; liveUpdate() }) {
                        domKey("env")
                        step(0.1)
                        label("Env Depth")
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
