package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.forms.formController
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Tool singleton ───────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing super saw parameters: voices:freqSpread. */
object SprudelSuperSawEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Super Saw Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelSuperSawEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelSuperSawEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelSuperSawEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelSuperSawEditorComp.Props(toolCtx, embedded)) { SprudelSuperSawEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class SuperSawPreset(
    val name: String,
    val voices: Int,
    val freqSpread: Double,
)

private val PRESETS = listOf(
    SuperSawPreset("Thin", 3, 0.1),
    SuperSawPreset("Classic", 5, 0.2),
    SuperSawPreset("Fat", 7, 0.3),
    SuperSawPreset("Wide", 5, 0.5),
    SuperSawPreset("Tight", 7, 0.08),
    SuperSawPreset("Massive", 9, 0.4),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelSuperSawEditorComp(ctx: Ctx<Props>) : Component<SprudelSuperSawEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseInput(): List<String> {
        val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
        if (raw.isBlank()) return emptyList()
        return raw.split(":").map { it.trim() }
    }

    private val parsedParts = parseInput()

    private var voices by value(parsedParts.getOrNull(0)?.toIntOrNull() ?: 5)
    private var freqSpread by value(parsedParts.getOrNull(1)?.toDoubleOrNull() ?: 0.2)

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"$voices:${freqSpread.fmt()}\""

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
        val p = parseInput()
        voices = p.getOrNull(0)?.toIntOrNull() ?: 5
        freqSpread = p.getOrNull(1)?.toDoubleOrNull() ?: 0.2
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    private fun applyPreset(preset: SuperSawPreset) {
        voices = preset.voices
        freqSpread = preset.freqSpread
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
                key = "supersaw-editor"
                css { minWidth = 400.px }
                toolHeaderWithInfo("Super Saw", props.toolCtx, infoPopup)
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
            key = "supersaw-editor-content-$resetCounter"

            // Presets
            div {
                key = "supersaw-presets"
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 4.px
                    marginBottom = 8.px
                }
                val matchedPreset = PRESETS.find {
                    it.voices == voices && it.freqSpread == freqSpread
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
                key = "supersaw-editor-form"
                ui.two.stackable.fields {
                    key = "supersaw-editor-fields"
                    UiInputField(voices, { voices = it; liveUpdate() }) {
                        domKey("voices")
                        step(1)
                        label {
                            +"Voices"
                            subFieldInfoIcon("params", "voices", props.toolCtx, infoPopup)
                        }
                    }
                    UiInputField(freqSpread, { freqSpread = it; liveUpdate() }) {
                        domKey("freqSpread")
                        step(0.01)
                        label {
                            +"Spread"
                            subFieldInfoIcon("params", "freqSpread", props.toolCtx, infoPopup)
                        }
                    }
                }
            }
        }
    }
}
