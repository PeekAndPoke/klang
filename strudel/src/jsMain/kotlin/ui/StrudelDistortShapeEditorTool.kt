package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.*
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.*

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for selecting a distortion waveshaper shape. */
object StrudelDistortShapeEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Distort Shape"

    override val iconFn: SemanticIconFn = { bolt }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelDistortShapeEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelDistortShapeEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelDistortShapeEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelDistortShapeEditorComp.Props(toolCtx, embedded)) { StrudelDistortShapeEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelDistortShapeEditorComp(ctx: Ctx<Props>) :
    Component<StrudelDistortShapeEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    companion object {
        val shapes = listOf("soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp")
        val allOptions = listOf("default") + shapes
    }

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            if (raw in allOptions) raw else "default"
        }

    private var shape by value(parsed)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$shape\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = (props.toolCtx.currentValue ?: "") != buildValue()

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
        shape = parsed
        props.toolCtx.onCommit(initialValue)
    }

    private fun onCommit() {
        props.toolCtx.onCommit(buildValue())
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                ui.small.header { +"Distort Shape" }
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
            key = "distort-shape-editor-content"

            div {
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 6.px
                    marginBottom = 8.px
                }
                for (s in allOptions) {
                    val isSelected = shape == s
                    ui.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                        key = s
                        onClick { shape = s; liveUpdate() }
                        +s
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
        val h = 100.0
        val padL = 22.0
        val padR = 6.0
        val padT = 6.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB
        val midY = padT + drawH / 2.0

        val goldHex = laf.gold

        val numPoints = drawW.toInt()
        val points = buildString {
            for (i in 0 until numPoints) {
                val x = -1.0 + 2.0 * i / numPoints
                val driven = x * 3.0  // moderate drive to show shape character
                val y = waveshape(driven, shape)
                val px = padL + i.toDouble()
                val py = midY - y * (drawH / 2.0 * 0.9)
                if (i > 0) append(" ")
                append("$px,$py")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            svgLine(padL, midY, padL + drawW, midY, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(
                padL + drawW / 2, padT, padL + drawW / 2, padT + drawH,
                stroke = "rgba(255,255,255,0.1)", strokeWidth = "0.5",
            )
            svgLine(padL, padT + drawH, padL + drawW, padT, stroke = "rgba(255,255,255,0.08)", strokeWidth = "0.5")
            svgPolyline(
                points = points,
                stroke = goldHex,
                strokeWidth = "1.5",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )
            svgText(padL - 3, midY + 2, "0", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL - 3, padT + 4, "1", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL - 3, padT + drawH, "-1", fill = "#ccc", fontSize = "5", textAnchor = "end")
            svgText(padL + drawW / 2, h - 4, "Input", fill = "#ccc", fontSize = "5", textAnchor = "middle")
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
        else -> tanh(x)
    }

    private fun tanh(x: Double): Double {
        val e2x = exp(2.0 * x)
        return (e2x - 1.0) / (e2x + 1.0)
    }
}
