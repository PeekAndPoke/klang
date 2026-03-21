package io.peekandpoke.klang.sprudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.comp.NoteStaffComp
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Public tool singleton ─────────────────────────────────────────────────────

/**
 * [KlangUiTool] for editing a mini-notation scale pattern string (e.g. `"c4:major d4:dorian"`).
 *
 * Acts as a mini-notation editor; when an atom is selected the embedded
 * [SprudelSingleScaleEditorTool] is shown for editing that atom's scale value.
 */
object SprudelScaleEditorTool : KlangUiTool {
    override val title: String = "Scale Editor"
    override val iconFn: SemanticIconFn = { music }

    private val inner = SprudelMiniNotationEditorTool(atomTool = SprudelSingleScaleEditorTool)

    override fun FlowContent.render(ctx: KlangUiToolContext) = with(inner) { render(ctx) }
}

// ── Single-value scale editor (embedded atom tool) ────────────────────────────

/** Embeddable tool for editing a single scale value such as `"c4:major"`. Used as atom tool inside [SprudelScaleEditorTool]. */
private object SprudelSingleScaleEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Scale"
    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelSingleScaleEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelSingleScaleEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelSingleScaleEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelSingleScaleEditorComp.Props(toolCtx, embedded)) { SprudelSingleScaleEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelSingleScaleEditorComp(ctx: Ctx<Props>) : Component<SprudelSingleScaleEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse initial value ───────────────────────────────────────────────────

    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val initialValue = props.toolCtx.currentValue ?: ""

    /** Initial scale name derived from [initialValue] (e.g. `"c4:major"` → `"c4 major"`). */
    private val initialScaleName: String = run {
        val raw = initialValue.trim().removeSurrounding("\"")
        raw.replace(":", " ").replace("_", " ").ifBlank { "c4 major" }
    }

    private var pickedScaleName by value(initialScaleName)
    private var currentValue by value(initialValue)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String {
        val scale = Scale.get(pickedScaleName)
        if (scale.empty) return "\"c4:major\""
        val tonic = (scale.tonic ?: "C4").lowercase()
        val mode = scale.type.replace(" ", "_")
        return "\"$tonic:$mode\""
    }

    private fun currentScaleNotes(): List<String> = Scale.get(pickedScaleName).notes

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    /** Called after every field change in embedded mode — propagates live updates to the host. */
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
        pickedScaleName = initialScaleName
        currentValue = initialValue
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
                css { minWidth = 50.vw }
                toolHeaderWithInfo("Scale", props.toolCtx, infoPopup)
                renderContent()
                ui.divider {}
                noui.basic.segment {
                    css {
                        padding = Padding(0.px)
                        display = Display.flex
                        justifyContent = JustifyContent.spaceBetween
                        alignItems = Align.center
                        gap = 8.px
                    }
                    ui.small.basic.label { +buildValue() }
                    noui.basic.segment {
                        css { padding = Padding(0.px); display = Display.flex; gap = 8.px }
                        ui.mini.givenNot(autoUpdate) { basic }.given(autoUpdate) { with(KlangTheme().styles.goldButton()) }.button {
                            onClick { KlangToolAutoUpdate.toggle() }
                            icon.sync_alternate()
                            +"Auto-update"
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
                        if (!autoUpdate) {
                            ui.positive.givenNot(isCurrentModified) { disabled }.button {
                                onClick { onCommit() }
                                icon.check()
                                +"Update"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderContent() {
        scalePicker(pickedScaleName) { pickedScaleName = it; liveUpdate() }

        ui.divider {}

        val notes = currentScaleNotes()
        if (notes.isNotEmpty()) {
            NoteStaffComp(scaleName = pickedScaleName, range = (-notes.size)..notes.size)
        }

        ui.divider {}
    }
}
