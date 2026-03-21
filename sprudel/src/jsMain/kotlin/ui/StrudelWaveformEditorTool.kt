package io.peekandpoke.klang.sprudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.popups.PopupsManager.Companion.popups
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
import kotlin.math.PI
import kotlin.math.sin

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for selecting a waveform. */
object StrudelWaveformEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Waveform"

    override val iconFn: SemanticIconFn = { wave_square }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelWaveformEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelWaveformEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelWaveformEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelWaveformEditorComp.Props(toolCtx, embedded)) { StrudelWaveformEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelWaveformEditorComp(ctx: Ctx<Props>) : Component<StrudelWaveformEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Available waveforms ───────────────────────────────────────────────────

    private val waveforms = listOf("sine", "triangle", "sawtooth", "square", "noise")

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            if (raw in waveforms) raw else "sine"
        }

    private var waveform by value(parsed)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$waveform\""

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
        waveform = parsed
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
                toolHeaderWithInfo("Waveform", props.toolCtx, infoPopup)
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
            key = "waveform-editor-content"

            div {
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 6.px
                    marginBottom = 8.px
                }
                for (wf in waveforms) {
                    val isSelected = waveform == wf
                    ui.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                        key = wf
                        onClick { waveform = wf; liveUpdate() }
                        +wf
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderWaveformSvg()
            }
        }
    }

    // ── SVG ───────────────────────────────────────────────────────────────────

    private fun FlowContent.renderWaveformSvg() {
        val w = 400.0
        val h = 80.0
        val padL = 10.0
        val padR = 10.0
        val padT = 8.0
        val padB = 8.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB
        val midY = padT + drawH / 2.0

        val numPoints = drawW.toInt()

        val points = buildString {
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints  // 0..1 (one cycle)
                val amplitude = when (waveform) {
                    "sine" -> sin(t * 2.0 * PI)
                    "triangle" -> if (t < 0.5) (4.0 * t - 1.0) else (3.0 - 4.0 * t)
                    "sawtooth" -> 2.0 * t - 1.0
                    "square" -> if (t < 0.5) 1.0 else -1.0
                    "noise" -> sin(i.toDouble() * 127.1) * sin(i.toDouble() * 311.7)
                    else -> 0.0
                }
                val x = padL + i.toDouble()
                val y = midY - amplitude * (drawH / 2.0 * 0.9)
                if (i > 0) append(" ")
                append("$x,$y")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            svgLine(padL, midY, padL + drawW, midY, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgPolyline(
                points = points,
                stroke = laf.gold,
                strokeWidth = "1",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )
        }
    }
}
