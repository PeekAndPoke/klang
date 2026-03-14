package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.forms.formController
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
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.max

// ── Tool singleton ───────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a compressor string `"threshold:ratio:knee:attack:release"`. */
object StrudelCompressorEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Compressor"

    override val iconFn: SemanticIconFn = { compress }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelCompressorEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelCompressorEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelCompressorEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelCompressorEditorComp.Props(toolCtx, embedded)) { StrudelCompressorEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class CompressorPreset(
    val name: String,
    val threshold: Double,
    val ratio: Double,
    val knee: Double,
    val attack: Double,
    val release: Double,
)

private val PRESETS = listOf(
    CompressorPreset("Gentle Leveling", -15.0, 2.0, 6.0, 0.01, 0.2),
    CompressorPreset("Punchy Drums", -20.0, 4.0, 3.0, 0.03, 0.1),
    CompressorPreset("Tight Percussion", -18.0, 6.0, 1.0, 0.005, 0.05),
    CompressorPreset("Vocal Smoothing", -12.0, 3.0, 8.0, 0.01, 0.15),
    CompressorPreset("Heavy Squeeze", -30.0, 8.0, 2.0, 0.005, 0.1),
    CompressorPreset("Sidechain Pump", -25.0, 10.0, 0.0, 0.001, 0.3),
    CompressorPreset("Brickwall Limiter", -2.0, 40.0, 0.0, 0.001, 0.05),
    CompressorPreset("Transparent Glue", -10.0, 2.0, 10.0, 0.02, 0.25),
)

// ── Component ────────────────────────────────────────────────────────────────

private class StrudelCompressorEditorComp(ctx: Ctx<Props>) : Component<StrudelCompressorEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    private val laf by subscribingTo(KlangTheme)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private val parsed
        get() = run {
            val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
            val parts = raw.split(":").map { it.toDoubleOrNull() }
            listOf(
                parts.getOrNull(0) ?: -20.0,   // threshold dB
                parts.getOrNull(1) ?: 4.0,      // ratio
                parts.getOrNull(2) ?: 6.0,      // knee dB
                parts.getOrNull(3) ?: 0.003,    // attack sec
                parts.getOrNull(4) ?: 0.1,      // release sec
            )
        }

    private var threshold by value(parsed[0])
    private var ratio by value(parsed[1])
    private var knee by value(parsed[2])
    private var attack by value(parsed[3])
    private var release by value(parsed[4])

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${threshold.fmt()}:${ratio.fmt()}:${knee.fmt()}:${attack.fmt()}:${release.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    private fun liveUpdate() {
        if (props.embedded) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() {
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        currentValue = initialValue
        threshold = parsed[0]
        ratio = parsed[1]
        knee = parsed[2]
        attack = parsed[3]
        release = parsed[4]
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    private fun applyPreset(preset: CompressorPreset) {
        threshold = preset.threshold
        ratio = preset.ratio
        knee = preset.knee
        attack = preset.attack
        release = preset.release
        formCtrl.resetAllFields()
        resetCounter++
        liveUpdate()
    }

    // ── Render ───────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "compressor-editor"
                css { minWidth = 560.px }
                ui.small.header { +"Compressor" }
                renderContent()
                ui.divider {}
                div {
                    css {
                        display = Display.flex
                        justifyContent = JustifyContent.flexEnd
                        gap = 8.px
                    }
                    ui.basic.button {
                        onClick { onCancel() }
                        icon.times()
                        +"Cancel"
                    }
                    ui.basic.givenNot(isInitialModified) { disabled }.button {
                        onClick { onReset() }
                        icon.undo()
                        +"Reset"
                    }
                    ui.black.givenNot(isCurrentModified) { disabled }.button {
                        onClick { onCommit() }
                        icon.check()
                        +"Update"
                    }
                }
            }
        }
    }

    private fun FlowContent.renderContent() {
        div {
            key = "compressor-editor-content-$resetCounter"

            // Presets
            div {
                key = "compressor-presets"
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 4.px
                    marginBottom = 8.px
                }
                for (preset in PRESETS) {
                    ui.mini.basic.button {
                        css {
                            whiteSpace = WhiteSpace.nowrap
                        }
                        onClick { applyPreset(preset) }
                        +preset.name
                    }
                }
            }

            ui.form {
                key = "compressor-editor-form"
                ui.five.stackable.fields {
                    key = "compressor-editor-fields"
                    UiInputField(threshold, { threshold = it; liveUpdate() }) {
                        domKey("threshold")
                        step(1.0)
                        label("Threshold")
                        rightLabel { ui.basic.label { +"dB" } }
                    }
                    UiInputField(ratio, { ratio = it; liveUpdate() }) {
                        domKey("ratio")
                        step(0.5)
                        label("Ratio")
                    }
                    UiInputField(knee, { knee = it; liveUpdate() }) {
                        domKey("knee")
                        step(0.5)
                        label("Knee")
                        rightLabel { ui.basic.label { +"dB" } }
                    }
                    UiInputField(attack, { attack = it; liveUpdate() }) {
                        domKey("attack")
                        step(0.001)
                        label("Attack")
                        rightLabel { ui.basic.label { +"sec" } }
                    }
                    UiInputField(release, { release = it; liveUpdate() }) {
                        domKey("release")
                        step(0.01)
                        label("Release")
                        rightLabel { ui.basic.label { +"sec" } }
                    }
                }
            }
            ui.divider {
                key = "compressor-editor-divider"
            }
            div {
                key = "compressor-editor-curve"
                css { if (!props.embedded) marginBottom = 1.rem }
                renderCompressorSvg()
            }
        }
    }

    // ── SVG visualization ────────────────────────────────────────────────────

    private fun FlowContent.renderCompressorSvg() {
        val w = 560.0
        val h = 140.0
        val padL = 32.0
        val padR = 12.0
        val padT = 10.0
        val padB = 22.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        // dB range: -60 to 0
        val dbMin = -60.0
        val dbMax = 0.0
        val dbRange = dbMax - dbMin

        fun dbToX(db: Double) = padL + ((db - dbMin) / dbRange) * drawW
        fun dbToY(db: Double) = padT + drawH - ((db - dbMin) / dbRange) * drawH

        // Compute the compressor transfer function
        val safeThreshold = threshold.coerceIn(-60.0, 0.0)
        val safeRatio = max(ratio, 1.0)
        val safeKnee = max(knee, 0.0)
        val halfKnee = safeKnee / 2.0

        fun outputDb(inputDb: Double): Double {
            return if (safeKnee <= 0.0) {
                // Hard knee
                if (inputDb <= safeThreshold) inputDb
                else safeThreshold + (inputDb - safeThreshold) / safeRatio
            } else {
                // Soft knee
                val kneeStart = safeThreshold - halfKnee
                val kneeEnd = safeThreshold + halfKnee
                when {
                    inputDb <= kneeStart -> inputDb
                    inputDb >= kneeEnd -> safeThreshold + (inputDb - safeThreshold) / safeRatio
                    else -> {
                        // Quadratic interpolation in the knee region
                        val x = inputDb - kneeStart
                        val kneeWidth = safeKnee
                        inputDb + ((1.0 / safeRatio - 1.0) * (x * x)) / (2.0 * kneeWidth)
                    }
                }
            }
        }

        // Build polyline points
        val step = 1.0
        val points = buildString {
            var db = dbMin
            var first = true
            while (db <= dbMax) {
                val outDb = outputDb(db).coerceIn(dbMin, dbMax)
                val x = dbToX(db)
                val y = dbToY(outDb)
                if (!first) append(" ")
                append("${x.toFixed(1)},${y.toFixed(1)}")
                first = false
                db += step
            }
        }

        // Fill path
        val fillPath = buildString {
            // Start at bottom-left
            append("M${dbToX(dbMin).toFixed(1)} ${dbToY(dbMin).toFixed(1)} ")
            var db = dbMin
            while (db <= dbMax) {
                val outDb = outputDb(db).coerceIn(dbMin, dbMax)
                append("L${dbToX(db).toFixed(1)} ${dbToY(outDb).toFixed(1)} ")
                db += step
            }
            // Close: go right to the unity line and back
            append("L${dbToX(dbMax).toFixed(1)} ${dbToY(dbMin).toFixed(1)} Z")
        }

        // Threshold marker
        val threshX = dbToX(safeThreshold)

        // Grid positions
        val gridDbs = listOf(-48.0, -36.0, -24.0, -12.0)

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")

            // Unity line (1:1 diagonal)
            svgLine(
                dbToX(dbMin), dbToY(dbMin),
                dbToX(dbMax), dbToY(dbMax),
                stroke = "rgba(255,255,255,0.1)", strokeWidth = "0.5",
            )

            // Grid lines
            for (db in gridDbs) {
                // Vertical
                svgLine(dbToX(db), padT, dbToX(db), padT + drawH, stroke = "rgba(255,255,255,0.08)", strokeWidth = "0.5")
                // Horizontal
                svgLine(padL, dbToY(db), padL + drawW, dbToY(db), stroke = "rgba(255,255,255,0.08)", strokeWidth = "0.5")
            }

            // Threshold line
            svgLine(threshX, padT, threshX, padT + drawH, stroke = "rgba(255,255,255,0.25)", strokeWidth = "0.5")

            // Fill under curve
            svgPath(d = fillPath, fill = "${laf.gold}26")

            // Compressor curve
            svgPath(
                d = "M${points.replace(" ", "L").replace(",", " ")}",
                stroke = laf.gold,
                strokeWidth = "1",
            )

            // Axis labels
            svgText(padL - 4, dbToY(0.0), "0", fill = "#ccc", fontSize = "7", textAnchor = "end")
            svgText(padL - 4, dbToY(-24.0), "-24", fill = "#ccc", fontSize = "7", textAnchor = "end")
            svgText(padL - 4, dbToY(-48.0), "-48", fill = "#ccc", fontSize = "7", textAnchor = "end")

            svgText(dbToX(0.0), h - 5, "0 dB", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(dbToX(-24.0), h - 5, "-24", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(dbToX(-48.0), h - 5, "-48", fill = "#ccc", fontSize = "7", textAnchor = "middle")

            // Threshold label
            svgText(threshX, padT - 2, "T", fill = "#ccc", fontSize = "7", textAnchor = "middle")
        }
    }
}
