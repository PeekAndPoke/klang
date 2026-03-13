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

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a delay time:feedback string. */
object StrudelDelayEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Delay Editor"

    override val iconFn: SemanticIconFn = { history }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelDelayEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelDelayEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelDelayEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelDelayEditorComp.Props(toolCtx, embedded)) { StrudelDelayEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelDelayEditorComp(ctx: Ctx<Props>) : Component<StrudelDelayEditorComp.Props>(ctx) {

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
                parts.getOrNull(0) ?: 0.25,   // delay time in beats
                parts.getOrNull(1) ?: 0.4,    // feedback
            )
        }

    private var delayTime by value(parsed.first)
    private var feedback by value(parsed.second)

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${delayTime.fmt()}:${feedback.fmt()}\""

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
        delayTime = parsed.first
        feedback = parsed.second
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
                ui.small.header { +"Delay" }
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
            key = "delay-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(delayTime, { delayTime = it; liveUpdate() }) {
                        domKey("delaytime")
                        step(0.01)
                        label("Delay Time")
                        rightLabel { ui.basic.label { +"beats" } }
                    }
                    UiInputField(feedback, { feedback = it; liveUpdate() }) {
                        domKey("feedback")
                        step(0.01)
                        label("Feedback")
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                unsafe { raw(buildDelaySvg()) }
            }
        }
    }

    // ── SVG ───────────────────────────────────────────────────────────────────

    private fun buildDelaySvg(): String {
        val w = 400.0
        val h = 80.0
        val padL = 10.0
        val padR = 10.0
        val padT = 8.0
        val padB = 8.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedTime = delayTime.coerceIn(0.01, 1.0)
        val clampedFeedback = feedback.coerceIn(0.0, 0.99)
        val intervalX = clampedTime * drawW
        val pulseW = (intervalX * 0.25).coerceAtLeast(4.0)

        val rects = buildString {
            // Original pulse
            val origH = drawH
            val origX = padL
            val origY = padT + (drawH - origH)
            val goldHex = laf.gold

            // Helper to convert hex color to rgba with opacity
            append("""<rect x="$origX" y="$origY" width="$pulseW" height="$origH" fill="$goldHex" rx="2"/>""")

            var amp = clampedFeedback
            var echoNum = 1
            while (amp > 0.02 && echoNum <= 8) {
                val echoX = padL + echoNum * intervalX
                if (echoX + pulseW > padL + drawW) break
                val echoH = drawH * amp
                val echoY = padT + (drawH - echoH)
                val opacity = (amp * 0.9).coerceIn(0.0, 1.0)
                val opacityHex = (opacity * 255).toInt().toString(16).padStart(2, '0')
                append("""<rect x="$echoX" y="$echoY" width="$pulseW" height="$echoH" fill="${goldHex}${opacityHex}" rx="2"/>""")
                amp *= clampedFeedback
                echoNum++
            }
        }

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="100%" style="display:block">
              <rect x="$padL" y="$padT" width="$drawW" height="$drawH" fill="#f8f8f8" rx="2"/>
              $rects
            </svg>
        """.trimIndent()
    }
}
