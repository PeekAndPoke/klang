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
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Tool singleton ───────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing pulse width parameter: duty. */
object SprudelPulzeEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Pulse Width Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelPulzeEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelPulzeEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelPulzeEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelPulzeEditorComp.Props(toolCtx, embedded)) { SprudelPulzeEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class PulzePreset(
    val name: String,
    val duty: Double,
)

private val PRESETS = listOf(
    PulzePreset("Square", 0.5),
    PulzePreset("Thin", 0.1),
    PulzePreset("Clarinet", 0.25),
    PulzePreset("Wide", 0.75),
    PulzePreset("Nasal", 0.15),
    PulzePreset("Hollow", 0.33),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelPulzeEditorComp(ctx: Ctx<Props>) : Component<SprudelPulzeEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseInput(): Double {
        val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
        return raw.toDoubleOrNull() ?: 0.5
    }

    private var duty by value(parseInput())

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${duty.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

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
        duty = parseInput()
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    private fun applyPreset(preset: PulzePreset) {
        duty = preset.duty
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
                key = "pulze-editor"
                css { minWidth = 400.px }
                toolHeaderWithInfo("Pulse Width", props.toolCtx, infoPopup)
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
            key = "pulze-editor-content-$resetCounter"

            // Presets
            div {
                key = "pulze-presets"
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 4.px
                    marginBottom = 8.px
                }
                val matchedPreset = PRESETS.find {
                    it.duty == duty
                }

                for (preset in PRESETS) {
                    val isSelected = preset === matchedPreset
                    ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                        css { whiteSpace = WhiteSpace.nowrap }
                        onClick { applyPreset(preset) }
                        +preset.name
                    }
                }

                val isCustom = matchedPreset == null
                ui.mini.givenNot(isCustom) { basic }.given(isCustom) { with(laf.styles.goldButton()) }.button {
                    css { whiteSpace = WhiteSpace.nowrap }
                    +"Custom"
                }
            }

            ui.divider()

            ui.form {
                key = "pulze-editor-form"
                ui.one.stackable.fields {
                    key = "pulze-editor-fields"
                    UiInputField(duty, { duty = it; liveUpdate() }) {
                        domKey("duty")
                        step(0.05)
                        label {
                            +"Duty"
                            subFieldInfoIcon("params", "duty", props.toolCtx, infoPopup)
                        }
                    }
                }
            }
        }
    }
}
