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

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a reverb room:wet string. */
object StrudelReverbEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Reverb Editor"

    override val iconFn: SemanticIconFn = { microphone }

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

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            val parts = raw.split(":").map { it.toDoubleOrNull() }
            Pair(
                parts.getOrNull(0) ?: 0.8,   // room
                parts.getOrNull(1) ?: 0.3,   // wet
            )
        }

    private var room by value(parsed.first)
    private var wet by value(parsed.second)

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${room.fmt()}:${wet.fmt()}\""

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
        room = parsed.first
        wet = parsed.second
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
                ui.small.header { +"Reverb" }
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
            key = "reverb-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(room, { room = it; liveUpdate() }) {
                        domKey("room")
                        step(0.01)
                        label("Room")
                    }
                    UiInputField(wet, { wet = it; liveUpdate() }) {
                        domKey("wet")
                        step(0.01)
                        label("Wet")
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                unsafe { raw(buildReverbSvg()) }
            }
        }
    }

    // ── SVG ───────────────────────────────────────────────────────────────────

    private fun buildReverbSvg(): String {
        val w = 400.0
        val h = 80.0
        val padL = 10.0
        val padR = 10.0
        val padT = 8.0
        val padB = 8.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val numBars = 40
        val barW = drawW / numBars - 1.0
        val clampedRoom = room.coerceIn(0.0, 1.0)
        val clampedWet = wet.coerceIn(0.0, 1.0)
        val decay = (1.0 - clampedRoom) * 0.2

        val bars = buildString {
            for (i in 0 until numBars) {
                val barH = (drawH * clampedWet * exp(-i * decay)).coerceIn(0.0, drawH)
                val barX = padL + i * (drawW / numBars)
                val barY = padT + (drawH - barH)

                // Background bar
                append("""<rect x="$barX" y="$padT" width="$barW" height="$drawH" fill="#e8e8e8" rx="1"/>""")
                // Filled bar
                if (barH > 0.5) {
                    append("""<rect x="$barX" y="$barY" width="$barW" height="$barH" fill="${laf.gold}99" rx="1"/>""")
                }
            }
        }

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="100%" style="display:block">
              $bars
            </svg>
        """.trimIndent()
    }
}
