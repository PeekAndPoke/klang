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

/** [KlangUiToolEmbeddable] for editing dust/crackle parameter: density. */
object SprudelDustEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Dust/Crackle Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelDustEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelDustEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelDustEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelDustEditorComp.Props(toolCtx, embedded)) { SprudelDustEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class DustPreset(
    val name: String,
    val density: Double,
)

private val PRESETS = listOf(
    DustPreset("Sparse", 0.05),
    DustPreset("Light", 0.1),
    DustPreset("Medium", 0.2),
    DustPreset("Dense", 0.5),
    DustPreset("Heavy", 0.8),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelDustEditorComp(ctx: Ctx<Props>) : Component<SprudelDustEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseInput(): Double {
        val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
        return raw.toDoubleOrNull() ?: 0.2
    }

    private var density by value(parseInput())

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"${density.fmt()}\""

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
        density = parseInput()
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    private fun applyPreset(preset: DustPreset) {
        density = preset.density
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
                key = "dust-editor"
                css { minWidth = 400.px }
                toolHeaderWithInfo("Dust/Crackle", props.toolCtx, infoPopup)
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
            key = "dust-editor-content-$resetCounter"

            // Presets
            div {
                key = "dust-presets"
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 4.px
                    marginBottom = 8.px
                }
                val matchedPreset = PRESETS.find {
                    it.density == density
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
                key = "dust-editor-form"
                ui.one.stackable.fields {
                    key = "dust-editor-fields"
                    UiInputField(density, { density = it; liveUpdate() }) {
                        domKey("density")
                        step(0.01)
                        label {
                            +"Density"
                            subFieldInfoIcon("params", "density", props.toolCtx, infoPopup)
                        }
                    }
                }
            }
        }
    }
}
