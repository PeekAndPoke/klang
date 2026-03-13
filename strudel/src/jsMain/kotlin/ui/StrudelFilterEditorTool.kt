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
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a filter cutoff:resonance string. */
object StrudelFilterEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Filter Editor"

    override val iconFn: SemanticIconFn = { filter }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelFilterEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelFilterEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelFilterEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelFilterEditorComp.Props(toolCtx, embedded)) { StrudelFilterEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelFilterEditorComp(ctx: Ctx<Props>) : Component<StrudelFilterEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            val parts = raw.split(":").map { it.toDoubleOrNull() }
            Pair(
                parts.getOrNull(0) ?: 2000.0,  // cutoff Hz
                parts.getOrNull(1) ?: 1.0,     // resonance Q
            )
        }

    private var cutoff by value(parsed.first)
    private var resonance by value(parsed.second)

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${cutoff.fmt()}:${resonance.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = (props.toolCtx.currentValue ?: "") != buildValue()

    private fun liveUpdate() {
        if (props.embedded) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() {
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        cutoff = parsed.first
        resonance = parsed.second
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
                ui.small.header { +"Filter" }
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
            key = "filter-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(cutoff, { cutoff = it; liveUpdate() }) {
                        domKey("cutoff")
                        step(10.0)
                        label("Cutoff")
                        rightLabel { ui.basic.label { +"Hz" } }
                    }
                    UiInputField(resonance, { resonance = it; liveUpdate() }) {
                        domKey("resonance")
                        step(0.1)
                        label("Resonance (Q)")
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                unsafe { raw(buildFilterSvg()) }
            }
        }
    }

    // ── SVG ───────────────────────────────────────────────────────────────────

    private fun buildFilterSvg(): String {
        val w = 400.0
        val h = 100.0
        val padL = 20.0
        val padR = 20.0
        val padT = 10.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val logMin = ln(20.0)
        val logMax = ln(20000.0)

        val safeCutoff = cutoff.coerceAtLeast(1.0)

        // Build polyline points
        val points = buildString {
            val step = 1
            var first = true
            var x = padL.toInt()
            while (x <= (padL + drawW).toInt()) {
                val f = exp(logMin + (x - padL) / drawW * (logMax - logMin))
                val response = 1.0 / sqrt(1.0 + (f / safeCutoff).pow(4.0))
                val y = padT + drawH * (1.0 - response.coerceIn(0.0, 1.0))
                if (!first) append(" ")
                append("$x,$y")
                first = false
                x += step
            }
        }

        // Build fill path (close under the curve)
        val fillPath = buildString {
            append("M$padL ${padT + drawH} ")
            val step = 2
            var x = padL.toInt()
            while (x <= (padL + drawW).toInt()) {
                val f = exp(logMin + (x - padL) / drawW * (logMax - logMin))
                val response = 1.0 / sqrt(1.0 + (f / safeCutoff).pow(4.0))
                val y = padT + drawH * (1.0 - response.coerceIn(0.0, 1.0))
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

        fun freqLabel(x: Double, txt: String) =
            """<text x="$x" y="${h - 3}" text-anchor="middle" font-size="10" fill="#aaa">$txt</text>"""

        fun gridLine(x: Double) =
            """<line x1="$x" y1="$padT" x2="$x" y2="${padT + drawH}" stroke="#e8e8e8" stroke-width="1"/>"""

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="100%" style="display:block">
              ${gridLine(x100)} ${gridLine(x1k)} ${gridLine(x10k)}
              <path d="$fillPath" fill="${laf.gold}26" stroke="none"/>
              <polyline points="$points" fill="none" stroke="${laf.gold}" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/>
              ${freqLabel(x100, "100")}
              ${freqLabel(x1k, "1k")}
              ${freqLabel(x10k, "10k")}
            </svg>
        """.trimIndent()
    }
}
