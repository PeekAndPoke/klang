package io.peekandpoke.klang.codetools

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.forms.formController
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a single ADSR envelope string. */
object StrudelAdsrEditorTool : KlangUiToolEmbeddable {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelAdsrEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelAdsrEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelAdsrEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelAdsrEditorComp.Props(toolCtx, embedded)) { StrudelAdsrEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelAdsrEditorComp(ctx: Ctx<Props>) : Component<StrudelAdsrEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val formCtrl = formController()

    private val parsed = run {
        val raw = props.toolCtx.currentValue
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?: ""
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

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${attack.fmt()}:${decay.fmt()}:${sustain.fmt()}:${release.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    /** Called after every slider change in embedded mode — propagates live updates to the host. */
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
        attack = parsed[0]
        decay = parsed[1]
        sustain = parsed[2]
        release = parsed[3]
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 600.px }
                ui.small.header { +"ADSR Envelope" }
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
        ui.form {
            ui.four.stackable.fields {
                adsrRow("Attack", "sec", attack, 0.001) { attack = it; liveUpdate() }
                adsrRow("Decay", "sec", decay, 0.001) { decay = it; liveUpdate() }
                adsrRow("Sustain", "", sustain, 0.01) { sustain = it; liveUpdate() }
                adsrRow("Release", "sec", release, 0.001) { release = it; liveUpdate() }
            }
        }
        ui.divider {}
        div {
            css { if (!props.embedded) marginBottom = 1.rem }
            unsafe { raw(buildAdsrSvg()) }
        }
    }

    // ── SVG curve ─────────────────────────────────────────────────────────────

    private fun buildAdsrSvg(): String {
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

        val pts = "$x0,$yBot $x1,$yTop $x2,$ySus $x3,$ySus $x4,$yBot"
        val fill = "M$x0 ${yBot}L$x1 ${yTop}L$x2 ${ySus}L$x3 ${ySus}L$x4 ${yBot}Z"

        fun lx(x: Double) = """<line x1="$x" y1="$yBot" x2="$x" y2="$yTop" stroke="#e8e8e8" stroke-width="1" stroke-dasharray="3,3"/>"""
        fun label(x: Double, txt: String) = """<text x="$x" y="${h - 5}" text-anchor="middle" font-size="10" fill="#aaa">$txt</text>"""

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="100%" style="display:block">
              ${lx(x1)} ${lx(x2)} ${lx(x3)}
              <line x1="$x0" y1="$ySus" x2="$x4" y2="$ySus" stroke="#e0e0e0" stroke-width="1" stroke-dasharray="4,4"/>
              <path d="$fill" fill="rgba(33,133,208,0.12)" stroke="none"/>
              <polyline points="$pts" fill="none" stroke="#2185d0" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
              ${label((x0 + x1) / 2, "A")}
              ${label((x1 + x2) / 2, "D")}
              ${label((x2 + x3) / 2, "S")}
              ${label((x3 + x4) / 2, "R")}
            </svg>
        """.trimIndent()
    }

    private fun FlowContent.adsrRow(
        label: String,
        unit: String,
        value: Double,
        step: Double,
        onChange: (Double) -> Unit,
    ) {
        UiInputField(value, onChange) {
            step(step)
            label(label)
            if (unit.isNotEmpty()) rightLabel { ui.basic.label { +unit } }
        }
    }
}
