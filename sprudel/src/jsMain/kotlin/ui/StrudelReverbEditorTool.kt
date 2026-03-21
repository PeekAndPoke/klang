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
import kotlin.math.exp

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing reverb parameters: room:size:fade:lowpass:dim. */
object StrudelReverbEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Reverb Editor"

    override val iconFn: SemanticIconFn = { sync_alternate }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelReverbEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelReverbEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelReverbEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelReverbEditorComp.Props(toolCtx, embedded)) { StrudelReverbEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelReverbEditorComp(ctx: Ctx<Props>) : Component<StrudelReverbEditorComp.Props>(ctx) {

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

    private var room by value(parsedParts.getOrNull(0) ?: 0.5)
    private var size by value(parsedParts.getOrNull(1) ?: 1.0)
    private var fade by value(parsedParts.getOrNull(2))
    private var lowpass by value(parsedParts.getOrNull(3))
    private var dim by value(parsedParts.getOrNull(4))

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String {
        val parts = mutableListOf(room.fmt(), size.fmt())

        // Only include trailing optional fields if they are set
        val optionals = listOf(fade, lowpass, dim)
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
        room = p.getOrNull(0) ?: 0.5
        size = p.getOrNull(1) ?: 1.0
        fade = p.getOrNull(2)
        lowpass = p.getOrNull(3)
        dim = p.getOrNull(4)
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
                toolHeaderWithInfo("Reverb", props.toolCtx, infoPopup)
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
            key = "reverb-editor-content-$resetCounter"

            ui.form {
                ui.five.stackable.fields {
                    UiInputField(room, { room = it; liveUpdate() }) {
                        domKey("room")
                        step(0.01)
                        label {
                            +"Room (wet/dry)"
                            subFieldInfoIcon("params", "room", props.toolCtx, infoPopup)
                        }
                    }
                    UiInputField(size, { size = it; liveUpdate() }) {
                        domKey("size")
                        step(0.1)
                        label {
                            +"Size (0–10)"
                            subFieldInfoIcon("params", "size", props.toolCtx, infoPopup)
                        }
                    }
                    nullableField("fade", "Fade (s)", 0.01, fade, subField = "fade") { fade = it; liveUpdate() }
                    nullableField("lowpass", "Lowpass (Hz)", 100.0, lowpass, subField = "lowpass") { lowpass = it; liveUpdate() }
                    nullableField("dim", "Dim (Hz)", 100.0, dim, subField = "dim") { dim = it; liveUpdate() }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderReverbCurve()
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
                    subFieldInfoIcon("params", subField, props.toolCtx, infoPopup)
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

    // ── SVG curve ─────────────────────────────────────────────────────────────

    private fun FlowContent.renderReverbCurve() {
        val w = 400
        val h = 90
        val padL = 22.0
        val padR = 6.0
        val padT = 6.0
        val padB = 24.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedRoom = room.coerceIn(0.0, 1.0)
        val clampedSize = size.coerceIn(0.1, 10.0)
        val effectiveFade = (fade ?: (clampedSize * 0.5)).coerceIn(0.01, 20.0)
        val decay = 3.0 / effectiveFade

        val numPoints = 100
        val points = (0..numPoints).map { i ->
            val t = i.toDouble() / numPoints
            val x = padL + t * drawW
            val y = padT + drawH - drawH * clampedRoom * exp(-t * numPoints / 10.0 * decay)
            x to y
        }

        val pathData = points.mapIndexed { i, (x, y) ->
            if (i == 0) "M $x $y" else "L $x $y"
        }.joinToString(" ")

        val fillPath = "$pathData L ${padL + drawW} ${padT + drawH} L $padL ${padT + drawH} Z"

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")

            // Y-axis grid lines and tick labels
            for (v in listOf(0.0, 0.5, 1.0)) {
                val y = padT + drawH - drawH * v
                svgLine(padL, y, padL + drawW, y, stroke = "rgba(255,255,255,0.2)", strokeWidth = "0.5")
                svgText(padL - 3, y + 2, v.fmt(), fill = "#ccc", fontSize = "5", textAnchor = "end")
            }

            // X-axis tick marks and labels
            for (i in 0..10) {
                val t = i / 10.0
                val x = padL + t * drawW
                val tickH = if (i % 5 == 0) 4.0 else 2.0
                svgLine(
                    x1 = x,
                    y1 = padT + drawH,
                    x2 = x,
                    y2 = padT + drawH + tickH,
                    stroke = if (i % 5 == 0) "#bbb" else "#ddd",
                    strokeWidth = "0.5"
                )
                if (i % 5 == 0) {
                    val label = when (i) {
                        0 -> "0"
                        5 -> "0.5"
                        10 -> "1"
                        else -> ""
                    }
                    svgText(x = x, y = padT + drawH + 10, text = label, fill = "#ccc", fontSize = "5", textAnchor = "middle")
                }
            }

            // Fill under curve
            svgPath(d = fillPath, fill = "${laf.gold}33")

            // Curve line
            svgPath(d = pathData, stroke = laf.gold, strokeWidth = "1")

            // Y-axis label (rotated)
            svgText(
                x = 4, y = padT + drawH / 2,
                text = "Level", fill = "#ccc", fontSize = "5", textAnchor = "middle",
                transform = "rotate(-90, 4, ${padT + drawH / 2})",
            )

            // X-axis label
            svgText(x = padL + drawW / 2, y = h - 2, text = "Time", fill = "#ccc", fontSize = "5", textAnchor = "middle")
        }
    }
}
