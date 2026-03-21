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

/** [KlangUiToolEmbeddable] for editing tremolo parameters: depth:rate:shape:skew:phase. */
object SprudelTremoloEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Tremolo Editor"

    override val iconFn: SemanticIconFn = { wave_square }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelTremoloEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelTremoloEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelTremoloEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelTremoloEditorComp.Props(toolCtx, embedded)) { SprudelTremoloEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelTremoloEditorComp(ctx: Ctx<Props>) : Component<SprudelTremoloEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    companion object {
        private val shapes = listOf("sine", "triangle", "square", "saw")
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

    private var depth by value(parsedParts.getOrNull(0)?.toDoubleOrNull() ?: 0.5)
    private var rate by value(parsedParts.getOrNull(1)?.toDoubleOrNull())
    private var shape by value(parsedParts.getOrNull(2)?.takeIf { it in shapes })
    private var skew by value(parsedParts.getOrNull(3)?.toDoubleOrNull())
    private var phase by value(parsedParts.getOrNull(4)?.toDoubleOrNull())

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String {
        val parts = mutableListOf(depth.fmt())

        val optionals = listOf(
            rate?.fmt(),
            shape,
            skew?.fmt(),
            phase?.fmt(),
        )
        val lastSetIndex = optionals.indexOfLast { it != null }

        if (lastSetIndex >= 0) {
            for (i in 0..lastSetIndex) {
                parts.add(optionals[i] ?: "")
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
        depth = p.getOrNull(0)?.toDoubleOrNull() ?: 0.5
        rate = p.getOrNull(1)?.toDoubleOrNull()
        shape = p.getOrNull(2)?.takeIf { it in shapes }
        skew = p.getOrNull(3)?.toDoubleOrNull()
        phase = p.getOrNull(4)?.toDoubleOrNull()
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
                toolHeaderWithInfo("Tremolo", props.toolCtx, infoPopup)
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
            key = "tremolo-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(depth, { depth = it; liveUpdate() }) {
                        domKey("depth")
                        step(0.01)
                        label {
                            +"Depth"
                            subFieldInfoIcon("params", "depth", props.toolCtx, infoPopup)
                        }
                    }
                    nullableField("rate", "Rate (cycles)", 0.5, rate, subField = "rate") { rate = it; liveUpdate() }
                }
            }

            // Shape buttons
            div {
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 6.px
                    marginTop = 8.px
                    marginBottom = 8.px
                }
                for (s in shapes) {
                    val isSelected = shape == s
                    ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                        key = s
                        onClick {
                            shape = if (isSelected) null else s
                            liveUpdate()
                        }
                        +s
                    }
                }
            }

            ui.form {
                ui.two.stackable.fields {
                    nullableField("skew", "Skew", 0.01, skew, subField = "skew") { skew = it; liveUpdate() }
                    nullableField("phase", "Phase", 0.01, phase, subField = "phase") { phase = it; liveUpdate() }
                }
            }

            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderTremoloViz()
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

    // ── SVG visualization ────────────────────────────────────────────────────

    private fun FlowContent.renderTremoloViz() {
        val w = 400.0
        val h = 80.0
        val padL = 10.0
        val padR = 10.0
        val padT = 8.0
        val padB = 8.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedDepth = depth.coerceIn(0.0, 1.0)
        val clampedRate = (rate ?: 4.0).coerceIn(0.5, 32.0)
        val clampedPhase = (phase ?: 0.0)
        val goldHex = laf.gold

        val numPoints = drawW.toInt()

        val points = buildString {
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints  // 0..1 (one cycle)

                // Generate LFO waveform based on shape
                val lfoPhase = (t * clampedRate + clampedPhase) % 1.0
                val lfoRaw = when (shape) {
                    "square" -> if (lfoPhase < 0.5) 1.0 else -1.0
                    "triangle" -> if (lfoPhase < 0.5) (4.0 * lfoPhase - 1.0) else (3.0 - 4.0 * lfoPhase)
                    "saw" -> 2.0 * lfoPhase - 1.0
                    else -> sin(lfoPhase * 2.0 * PI) // sine (default)
                }

                val x = padL + i.toDouble()
                val y = padT + drawH / 2.0 - (lfoRaw * clampedDepth * drawH / 2.0)
                if (i > 0) append(" ")
                append("$x,$y")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            svgLine(padL, padT + drawH / 2.0, padL + drawW, padT + drawH / 2.0, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgPolyline(
                points = points,
                stroke = goldHex,
                strokeWidth = "1",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )
        }
    }
}
