package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.*
import kotlinx.html.*

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for selecting a sample by name. */
object StrudelSampleEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Sample Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelSampleEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelSampleEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelSampleEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelSampleEditorComp.Props(toolCtx, embedded)) { StrudelSampleEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelSampleEditorComp(ctx: Ctx<Props>) : Component<StrudelSampleEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Sample groups ─────────────────────────────────────────────────────────

    private val sampleGroups = listOf(
        "Drums" to listOf("bd", "sd", "hh", "oh", "cp", "mt", "ht", "lt", "cr", "cy", "cb"),
        "Percussion" to listOf("tabla", "tabla2", "casio", "diphone", "diphone2"),
        "Bass" to listOf("bass", "bass2", "bass3", "bass0"),
        "Synth" to listOf("sine", "triangle", "sawtooth", "square", "supersaw", "noise"),
        "GM" to listOf("gm_recorder", "gm_xylophone", "gm_piano", "gm_violin", "gm_trumpet"),
        "Other" to listOf("breaks", "reverbkick", "space", "voice", "pluck", "casio"),
    )

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            raw.ifBlank { "bd" }
        }

    private var sample by value(parsed)
    private var search by value("")

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$sample\""

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
        sample = parsed
        search = ""
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
                ui.small.header { +"Sample" }
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
            key = "sample-editor-content"

            // Search input
            input(type = InputType.text) {
                placeholder = "Search samples..."
                value = search
                css {
                    width = 100.pct
                    marginBottom = 8.px
                    padding = Padding(6.px, 10.px)
                    border = Border(1.px, BorderStyle.solid, Color("#ddd"))
                    borderRadius = 4.px
                }
                onInput { e -> search = e.target?.asDynamic()?.value as? String ?: "" }
            }

            // Sample groups
            val searchTerm = search.trim().lowercase()
            for ((groupName, samples) in sampleGroups) {
                val filtered = if (searchTerm.isEmpty()) {
                    samples
                } else {
                    samples.filter { it.contains(searchTerm, ignoreCase = true) }
                }
                if (filtered.isEmpty()) continue

                ui.small.header { +groupName }
                div {
                    css {
                        display = Display.flex
                        flexWrap = FlexWrap.wrap
                        gap = 4.px
                        marginBottom = 8.px
                    }
                    for (name in filtered) {
                        val isSelected = sample == name
                        ui.mini.givenNot(isSelected) { basic }.button {
                            key = name
                            onClick { sample = name; liveUpdate() }
                            +name
                        }
                    }
                }
            }
        }
    }
}
