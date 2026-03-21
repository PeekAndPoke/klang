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
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.*
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.PI
import kotlin.math.sin

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing phaser parameters: rate:depth:center:sweep. */
object SprudelPhaserEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Phaser Editor"

    override val iconFn: SemanticIconFn = { sync_alternate }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelPhaserEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelPhaserEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelPhaserEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelPhaserEditorComp.Props(toolCtx, embedded)) { SprudelPhaserEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelPhaserEditorComp(ctx: Ctx<Props>) : Component<SprudelPhaserEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private fun parseInput(): List<Double?> {
        val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
        if (raw.isBlank()) return emptyList()
        return raw.split(":").map { it.trim().toDoubleOrNull() }
    }

    private val parsedParts = parseInput()

    private var rate by value(parsedParts.getOrNull(0) ?: 0.5)
    private var depth by value(parsedParts.getOrNull(1))
    private var center by value(parsedParts.getOrNull(2))
    private var sweep by value(parsedParts.getOrNull(3))

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String {
        val parts = mutableListOf(rate.fmt())

        val optionals = listOf(depth, center, sweep)
        val lastSetIndex = optionals.indexOfLast { it != null }

        if (lastSetIndex >= 0) {
            for (i in 0..lastSetIndex) {
                parts.add(optionals[i]?.fmt() ?: "")
            }
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
        rate = p.getOrNull(0) ?: 0.5
        depth = p.getOrNull(1)
        center = p.getOrNull(2)
        sweep = p.getOrNull(3)
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
                toolHeaderWithInfo("Phaser", props.toolCtx, infoPopup)
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
            key = "phaser-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(rate, { rate = it; liveUpdate() }) {
                        domKey("rate")
                        step(0.1)
                        label {
                            +"Rate (Hz)"
                            subFieldInfoIcon("rate", "rate", props.toolCtx, infoPopup)
                        }
                    }
                    nullableField("depth", "Depth", 0.01, depth, subField = "depth") { depth = it; liveUpdate() }
                }
                ui.two.stackable.fields {
                    nullableField("center", "Center (Hz)", 10.0, center, subField = "center") { center = it; liveUpdate() }
                    nullableField("sweep", "Sweep (Hz)", 10.0, sweep, subField = "sweep") { sweep = it; liveUpdate() }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderPhaserViz()
            }
        }
    }

    private fun FlowContent.nullableField(
        key: String,
        labelText: String,
        stepVal: Double,
        current: Double?,
        subField: String? = null,
        onChange: (Double?) -> Unit,
    ) {
        UiInputField.nullable(current, { onChange(it) }) {
            domKey(key)
            step(stepVal)
            if (subField != null) {
                label {
                    +labelText
                    subFieldInfoIcon("rate", subField, props.toolCtx, infoPopup)
                }
            } else {
                label(labelText)
            }
            if (current == null) {
                placeholder("default")
            }
            rightLabel {
                ui.basic.icon.label {
                    css { cursor = Cursor.pointer }
                    onClick {
                        if (current != null) onChange(null) else onChange(0.0)
                    }
                    if (current != null) icon.times() else icon.plus()
                }
            }
        }
    }

    // ── SVG visualization ────────────────────────────────────────────────────

    private fun FlowContent.renderPhaserViz() {
        val w = 400.0
        val h = 90.0
        val padL = 22.0
        val padR = 6.0
        val padT = 6.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedRate = rate.coerceIn(0.01, 20.0)
        val clampedDepth = (depth ?: 0.5).coerceIn(0.0, 1.0)
        val clampedCenter = (center ?: 1000.0).coerceIn(100.0, 10000.0)
        val clampedSweep = (sweep ?: 500.0).coerceIn(0.0, 10000.0)
        val goldHex = laf.gold

        val numPoints = drawW.toInt()

        // Frequency sweep curve
        val freqPoints = buildString {
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints  // 0..1 (one cycle)
                val lfo = sin(t * clampedRate * 2.0 * PI)
                val freq = clampedCenter + lfo * clampedSweep * clampedDepth
                val normFreq = ((freq - 100.0) / 9900.0).coerceIn(0.0, 1.0)
                val px = padL + i.toDouble()
                val py = padT + drawH - normFreq * drawH
                if (i > 0) append(" ")
                append("$px,$py")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")

            // Center frequency line
            val centerNorm = ((clampedCenter - 100.0) / 9900.0).coerceIn(0.0, 1.0)
            val centerY = padT + drawH - centerNorm * drawH
            svgLine(padL, centerY, padL + drawW, centerY, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")

            // Frequency sweep
            svgPolyline(
                points = freqPoints,
                stroke = goldHex,
                strokeWidth = "1.5",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )

            // Axis labels
            svgText(
                x = 4, y = padT + drawH / 2,
                text = "Freq", fill = "#ccc", fontSize = "5", textAnchor = "middle",
                transform = "rotate(-90, 4, ${padT + drawH / 2})",
            )
            svgText(padL + drawW / 2, h - 4, "Time", fill = "#ccc", fontSize = "5", textAnchor = "middle")
        }
    }
}
