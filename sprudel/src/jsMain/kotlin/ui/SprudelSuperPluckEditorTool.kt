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

/** [KlangUiToolEmbeddable] for editing super pluck parameters: voices:freqSpread:decay:brightness:pickPosition:stiffness. */
object SprudelSuperPluckEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Super Pluck Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelSuperPluckEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelSuperPluckEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelSuperPluckEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelSuperPluckEditorComp.Props(toolCtx, embedded)) { SprudelSuperPluckEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class SuperPluckPreset(
    val name: String,
    val voices: Int,
    val freqSpread: Double,
    val decay: Double,
    val brightness: Double,
    val pickPosition: Double,
    val stiffness: Double,
)

private val PRESETS = listOf(
    SuperPluckPreset("12-String Guitar", 6, 0.15, 0.996, 0.5, 0.4, 0.15),
    SuperPluckPreset("Choir Harp", 7, 0.2, 0.998, 0.7, 0.6, 0.05),
    SuperPluckPreset("Shimmer Pad", 7, 0.3, 0.999, 0.4, 0.5, 0.0),
    SuperPluckPreset("Thick Guitar", 5, 0.1, 0.996, 0.6, 0.45, 0.2),
    SuperPluckPreset("Ukulele Chorus", 4, 0.12, 0.99, 0.8, 0.5, 0.1),
    SuperPluckPreset("Ethereal Strings", 7, 0.25, 0.998, 0.3, 0.55, 0.0),
    SuperPluckPreset("Metallic Cluster", 5, 0.3, 0.995, 0.9, 0.2, 0.5),
    SuperPluckPreset("Tight Pizzicato", 3, 0.08, 0.93, 0.3, 0.5, 0.0),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelSuperPluckEditorComp(ctx: Ctx<Props>) : Component<SprudelSuperPluckEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseInput(): List<Double?> {
        val raw = currentValue.trim().removePrefix("\"").removeSuffix("\"")
        if (raw.isBlank()) return emptyList()
        return raw.split(":").map { it.trim().toDoubleOrNull() }
    }

    private val parsedParts = parseInput()

    private var voices by value(parsedParts.getOrNull(0)?.toInt() ?: 5)
    private var freqSpread by value(parsedParts.getOrNull(1) ?: 0.2)
    private var decay by value(parsedParts.getOrNull(2) ?: 0.996)
    private var brightness by value(parsedParts.getOrNull(3) ?: 0.5)
    private var pickPosition by value(parsedParts.getOrNull(4) ?: 0.5)
    private var stiffness by value(parsedParts.getOrNull(5) ?: 0.0)

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        "\"$voices:${freqSpread.fmt()}:${decay.fmt()}:${brightness.fmt()}:${pickPosition.fmt()}:${stiffness.fmt()}\""

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
        voices = p.getOrNull(0)?.toInt() ?: 5
        freqSpread = p.getOrNull(1) ?: 0.2
        decay = p.getOrNull(2) ?: 0.996
        brightness = p.getOrNull(3) ?: 0.5
        pickPosition = p.getOrNull(4) ?: 0.5
        stiffness = p.getOrNull(5) ?: 0.0
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(currentValue)
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        props.toolCtx.onCommit(currentValue)
    }

    private fun applyPreset(preset: SuperPluckPreset) {
        voices = preset.voices
        freqSpread = preset.freqSpread
        decay = preset.decay
        brightness = preset.brightness
        pickPosition = preset.pickPosition
        stiffness = preset.stiffness
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
                key = "superpluck-editor"
                css { minWidth = 560.px }
                toolHeaderWithInfo("Super Pluck", props.toolCtx, infoPopup)
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
            key = "superpluck-editor-content-$resetCounter"

            // Presets
            div {
                key = "superpluck-presets"
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 4.px
                    marginBottom = 8.px
                }
                val matchedPreset = PRESETS.find {
                    it.voices == voices && it.freqSpread == freqSpread && it.decay == decay &&
                            it.brightness == brightness && it.pickPosition == pickPosition && it.stiffness == stiffness
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
                key = "superpluck-editor-form"

                // Row 1: Voices + Spread
                ui.two.stackable.fields {
                    key = "superpluck-editor-unison"
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
                            +"Spread (st)"
                            subFieldInfoIcon("params", "freqSpread", props.toolCtx, infoPopup)
                        }
                    }
                }

                // Row 2: String params
                ui.four.stackable.fields {
                    key = "superpluck-editor-string"
                    UiInputField(decay, { decay = it; liveUpdate() }) {
                        domKey("decay")
                        step(0.001)
                        label {
                            +"Decay"
                            subFieldInfoIcon("params", "decay", props.toolCtx, infoPopup)
                        }
                    }
                    UiInputField(brightness, { brightness = it; liveUpdate() }) {
                        domKey("brightness")
                        step(0.05)
                        label {
                            +"Brightness"
                            subFieldInfoIcon("params", "brightness", props.toolCtx, infoPopup)
                        }
                    }
                    UiInputField(pickPosition, { pickPosition = it; liveUpdate() }) {
                        domKey("pickPosition")
                        step(0.05)
                        label {
                            +"Pick Pos"
                            subFieldInfoIcon("params", "pickPosition", props.toolCtx, infoPopup)
                        }
                    }
                    UiInputField(stiffness, { stiffness = it; liveUpdate() }) {
                        domKey("stiffness")
                        step(0.05)
                        label {
                            +"Stiffness"
                            subFieldInfoIcon("params", "stiffness", props.toolCtx, infoPopup)
                        }
                    }
                }
            }
        }
    }
}
