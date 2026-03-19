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
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
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

    /** Entry with a canonical [name] shown as button, plus [aliases] that resolve to it. */
    private data class SampleEntry(val name: String, val aliases: List<String> = emptyList()) {
        val allNames get() = listOf(name) + aliases
    }

    private val sampleGroups = listOf(
        "Synth" to listOf(
            SampleEntry("sin", listOf("sine")),
            SampleEntry("tri", listOf("triangle")),
            SampleEntry("saw", listOf("sawtooth")),
            SampleEntry("sqr", listOf("square", "pulse")),
            SampleEntry("zaw", listOf("zawtooth")),
            SampleEntry("supersaw"),
            SampleEntry("pulze"),
            SampleEntry("impulse"),
            SampleEntry("silence"),
        ),
        "Noise" to listOf(
            SampleEntry("white", listOf("whitenoise")),
            SampleEntry("brown", listOf("brownnoise")),
            SampleEntry("pink", listOf("pinknoise")),
            SampleEntry("dust"),
            SampleEntry("crackle"),
        ),
        "Drums" to listOf("bd", "sd", "hh", "oh", "cr", "rd", "cp", "mt", "ht", "lt", "cb").map { SampleEntry(it) },
        "GM" to listOf("gm_recorder", "gm_xylophone", "gm_piano", "gm_violin", "gm_trumpet").map { SampleEntry(it) },
    )

    /** Maps every alias to its canonical name. */
    private val aliasToCanonical: Map<String, String> = buildMap {
        for ((_, entries) in sampleGroups) {
            for (entry in entries) {
                for (alias in entry.aliases) {
                    put(alias, entry.name)
                }
            }
        }
    }

    // ── Parse current value from raw source text ──────────────────────────────

    @Suppress("unused")
    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            val name = raw.ifBlank { "bd" }
            aliasToCanonical[name] ?: name
        }

    private var sample by value(parsed)
    private var search by value("")

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$sample\""

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
            for ((groupName, entries) in sampleGroups) {
                val filtered = if (searchTerm.isEmpty()) {
                    entries
                } else {
                    entries.filter { entry ->
                        entry.allNames.any { it.contains(searchTerm, ignoreCase = true) }
                    }
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
                    for (entry in filtered) {
                        val isSelected = sample == entry.name
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            key = entry.name
                            onClick { sample = entry.name; liveUpdate() }
                            +entry.name
                        }
                    }
                }
            }
        }
    }
}
