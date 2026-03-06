package io.peekandpoke.klang.codetools

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Registration helper ───────────────────────────────────────────────────────

/** [KlangUiTool] implementation that opens the [StrudelAdsrEditorComp]. */
val StrudelAdsrEditorTool = KlangUiTool { ctx ->
    StrudelAdsrEditorComp(ctx)
}

// ── Component ─────────────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun FlowContent.StrudelAdsrEditorComp(toolCtx: KlangUiToolContext) =
    comp(StrudelAdsrEditorComp.Props(toolCtx)) { StrudelAdsrEditorComp(it) }

@Suppress("FunctionName")
private fun Tag.StrudelAdsrEditorComp(toolCtx: KlangUiToolContext) =
    comp(StrudelAdsrEditorComp.Props(toolCtx)) { StrudelAdsrEditorComp(it) }

private class StrudelAdsrEditorComp(ctx: Ctx<Props>) : Component<StrudelAdsrEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext)

    // ── Parse current value from raw source text ──────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${attack.fmt()}:${decay.fmt()}:${sustain.fmt()}:${release.fmt()}\""

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        ui.segment {
            css { minWidth = 600.px }

            ui.small.header { +"ADSR Envelope" }

            ui.form {
                ui.four.stackable.fields {
                    adsrRow("Attack", "sec", attack, 0.001) { attack = it }
                    adsrRow("Decay", "sec", decay, 0.001) { decay = it }
                    adsrRow("Sustain", "", sustain, 0.01) { sustain = it }
                    adsrRow("Release", "sec", release, 0.001) { release = it }
                }
            }

            ui.divider {}

            div {
                css {
                    display = Display.flex
                    justifyContent = JustifyContent.flexEnd
                    gap = 8.px
                }

                ui.basic.button {
                    onClick { props.toolCtx.onCancel() }
                    +"Cancel"
                }
                ui.black.button {
                    onClick { props.toolCtx.onCommit(buildValue()) }
                    +"OK"
                }
            }
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
            step(step)
            label(label)
            if (unit.isNotEmpty()) rightLabel { ui.basic.label { +unit } }
        }
    }
}
