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
import kotlin.math.sign
import kotlin.math.sin

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing distort parameters: amount:shape. */
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
        val shapes = listOf("soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp")
    }

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private fun parseInput(): List<String> {
        val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
        if (raw.isBlank()) return emptyList()
        return raw.split(":").map { it.trim() }
    }

    private val parsedParts = parseInput()

    private var amount by value(parsedParts.getOrNull(0)?.toDoubleOrNull() ?: 0.5)
    private var shape by value(parsedParts.getOrNull(1)?.takeIf { it in shapes })
    private var oversample by value(parsedParts.getOrNull(2)?.toIntOrNull())

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String {
        val parts = mutableListOf(amount.fmt())
        if (shape != null || oversample != null) {
            parts.add(shape ?: "soft")
        }
        if (oversample != null && oversample!! > 1) {
            parts.add(oversample.toString())
        }
        return "\"${parts.joinToString(":")}\""
    }

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
        val p = parseInput()
        amount = p.getOrNull(0)?.toDoubleOrNull() ?: 0.5
        shape = p.getOrNull(1)?.takeIf { it in shapes }
        oversample = p.getOrNull(2)?.toIntOrNull()
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(initialValue)
        resetCounter++
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
                    UiInputField(amount, { amount = it; liveUpdate() }) {
                        domKey("amount")
                        step(0.01)
                        appear { three.wide }
                        label {
                            +"Amount"
                            subFieldInfoIcon("amount", "amount", props.toolCtx, infoPopup)
                        }
                    }

                    // Shape buttons
                    noui.twelve.wide.field {
                        label {
                            +"Shape"
                            subFieldInfoIcon("amount", "shape", props.toolCtx, infoPopup)
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
                                    onClick { shape = null; liveUpdate() }
                                    +"default"
                                }
                            for (s in shapes) {
                                val isSelected = shape == s
                                ui.small.givenNot(isSelected) { basic }
                                    .given(isSelected) { with(laf.styles.goldButton()) }.button {
                                        key = s
                                        onClick { shape = s; liveUpdate() }
                                        +s
                                    }
                            }
                        }
                    }
                }
            }

            // Oversampling buttons
            ui.form {
                noui.field {
                    label { +"Oversampling" }
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
                                    onClick { oversample = factor; liveUpdate() }
                                    +label
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
        else -> tanh(x)
    }

    private fun tanh(x: Double): Double {
        val e2x = exp(2.0 * x)
        return (e2x - 1.0) / (e2x + 1.0)
    }
}
