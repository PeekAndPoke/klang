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

// ── Configurable tool class ─────────────────────────────────────────────────

/** Configurable [KlangUiToolEmbeddable] for editing filter ADSR envelope strings. */
class StrudelFilterAdsrEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
) : KlangUiToolEmbeddable {

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelFilterAdsrEditorComp(ctx, embedded = false, title = title)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelFilterAdsrEditorComp(ctx, embedded = true, title = title)
    }
}

// ── Singleton instances ─────────────────────────────────────────────────────

object StrudelLpAdsrEditorTool : KlangUiToolEmbeddable by StrudelFilterAdsrEditorTool(
    title = "LP Filter Envelope",
    iconFn = { chart_area },
)

object StrudelHpAdsrEditorTool : KlangUiToolEmbeddable by StrudelFilterAdsrEditorTool(
    title = "HP Filter Envelope",
    iconFn = { chart_area },
)

object StrudelBpAdsrEditorTool : KlangUiToolEmbeddable by StrudelFilterAdsrEditorTool(
    title = "BP Filter Envelope",
    iconFn = { chart_area },
)

object StrudelNfAdsrEditorTool : KlangUiToolEmbeddable by StrudelFilterAdsrEditorTool(
    title = "Notch Filter Envelope",
    iconFn = { chart_area },
)

// ── Entry-point helper ──────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelFilterAdsrEditorComp(
    toolCtx: KlangUiToolContext,
    embedded: Boolean,
    title: String,
) = comp(
    StrudelFilterAdsrEditorComp.Props(toolCtx, embedded, title)
) {
    StrudelFilterAdsrEditorComp(it)
}

// ── Component ───────────────────────────────────────────────────────────────

private class StrudelFilterAdsrEditorComp(ctx: Ctx<Props>) : Component<StrudelFilterAdsrEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val embedded: Boolean = false,
        val title: String,
    )

    // ── Parse current value from raw source text ────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private val parsed
        get() = run {
            val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")

            val parts = raw.split(":").mapNotNull { it.toDoubleOrNull() }
            listOf(
                parts.getOrNull(0) ?: 0.01,   // attack
                parts.getOrNull(1) ?: 0.1,    // decay
                parts.getOrNull(2) ?: 0.8,    // sustain
                parts.getOrNull(3) ?: 0.3,    // release
            )
        }

    private var attack by value(parsed[0])
    private var decay by value(parsed[1])
    private var sustain by value(parsed[2])
    private var release by value(parsed[3])

    private var resetCounter by value(0)

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${attack.fmt()}:${decay.fmt()}:${sustain.fmt()}:${release.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    /** Called after every slider change in embedded mode — propagates live updates to the host. */
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
        attack = parsed[0]
        decay = parsed[1]
        sustain = parsed[2]
        release = parsed[3]
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    // ── Render ──────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "filter-adsr-editor"
                css { minWidth = 600.px }
                toolHeaderWithInfo(props.title, props.toolCtx, infoPopup)
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
            key = "filter-adsr-editor-content-$resetCounter"

            ui.form {
                key = "filter-adsr-editor-form"
                ui.four.stackable.fields {
                    key = "filter-adsr-editor-fields"
                    adsrRow("Attack", "sec", attack, 0.001) { attack = it; liveUpdate() }
                    adsrRow("Decay", "sec", decay, 0.001) { decay = it; liveUpdate() }
                    adsrRow("Sustain", "", sustain, 0.01) { sustain = it; liveUpdate() }
                    adsrRow("Release", "sec", release, 0.001) { release = it; liveUpdate() }
                }
            }
            ui.divider {
                key = "filter-adsr-editor-divider"
            }
            div {
                key = "filter-adsr-editor-curve"
                css { if (!props.embedded) marginBottom = 1.rem }
                renderAdsrSvg()
            }
        }
    }

    // ── SVG curve ───────────────────────────────────────────────────────────

    private fun FlowContent.renderAdsrSvg() {
        val w = 560.0
        val h = 120.0
        val padL = 12.0
        val padR = 12.0
        val padT = 10.0
        val padB = 22.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        // Sustain hold is a fixed visual segment so the shape is always readable
        val sustainHold = maxOf(attack + decay, 0.3)
        val totalTime = attack + decay + sustainHold + release
        val scale = drawW / totalTime

        val x0 = padL
        val x1 = x0 + attack * scale
        val x2 = x1 + decay * scale
        val x3 = x2 + sustainHold * scale
        val x4 = x3 + release * scale

        val yBot = padT + drawH
        val yTop = padT
        val ySus = padT + drawH * (1.0 - sustain.coerceIn(0.0, 1.0))

        val linePath = "M$x0 ${yBot}L$x1 ${yTop}L$x2 ${ySus}L$x3 ${ySus}L$x4 $yBot"
        val fillPath = "${linePath}Z"

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            // Dashed guide lines at phase boundaries
            svgLine(x1, yBot, x1, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x2, yBot, x2, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x3, yBot, x3, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // Sustain level line
            svgLine(x0, ySus, x4, ySus, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // Fill under envelope
            svgPath(d = fillPath, fill = "${laf.gold}26")
            // Envelope line
            svgPath(d = linePath, stroke = laf.gold, strokeWidth = "1")
            // Phase labels
            svgText((x0 + x1) / 2, h - 5, "A", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x1 + x2) / 2, h - 5, "D", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x2 + x3) / 2, h - 5, "S", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x3 + x4) / 2, h - 5, "R", fill = "#ccc", fontSize = "7", textAnchor = "middle")
        }
    }

    private fun FlowContent.adsrRow(
        label: String,
        unit: String,
        value: Double,
        step: Double,
        onChange: (Double) -> Unit,
    ) {
        UiInputField(value, onChange) {
            domKey(label)
            step(step)
            label(label)
            if (unit.isNotEmpty()) rightLabel { ui.basic.label { +unit } }
        }
    }
}
