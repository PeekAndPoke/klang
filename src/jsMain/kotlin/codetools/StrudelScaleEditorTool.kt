package io.peekandpoke.klang.codetools

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.semanticui.forms.old.select.SelectField
import de.peekandpoke.kraft.semanticui.forms.old.select.SelectFieldComponent
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.comp.NoteStaffComp
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import kotlinx.coroutines.flow.flowOf
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a single scale string (e.g. `"c4:major"`). */
object StrudelScaleEditorTool : KlangUiToolEmbeddable {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelScaleEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelScaleEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────
@Suppress("FunctionName")
private fun Tag.StrudelScaleEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelScaleEditorComp.Props(toolCtx, embedded)) { StrudelScaleEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelScaleEditorComp(ctx: Ctx<Props>) : Component<StrudelScaleEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Static data ───────────────────────────────────────────────────────────

    private val letters = listOf("c", "d", "e", "f", "g", "a", "b")

    /** Accidentals: stored value → display label */
    private val accidentals = listOf(
        "" to "♮",
        "#" to "#",
        "##" to "##",
        "###" to "###",
        "b" to "b",
        "bb" to "bb",
        "bbb" to "bbb",
    )

    /** All scale mode names (canonical + aliases) from the dictionary.
     *  Stored in state with spaces replaced by "_" so they are safe for mini-notation.
     *  Displayed to the user with the original spaces. */
    private val modeNames: List<String> = listOf(
        "major", "minor", "pentatonic", "chromatic"
    ).plus(
        ScaleTypeDictionary.all()
            .flatMap { listOf(it.name) + it.aliases }
            .sorted())

    // ── Parse current value ───────────────────────────────────────────────────

    private val parsed = run {
        // Strip surrounding quotes, e.g. "\"c4:major\"" → "c4:major"
        val raw = props.toolCtx.currentValue
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?: ""

        // Split root from mode on ":" (canonical) — normalise "_" → " " in mode
        val colonIdx = raw.indexOf(':')
        val rootPart = if (colonIdx >= 0) raw.substring(0, colonIdx) else raw
        val modePart = if (colonIdx >= 0) raw.substring(colonIdx + 1).replace("_", " ") else "major"

        // Parse letter (first char a-g), then accidental (longest match first), then octave
        val letter = rootPart.firstOrNull()?.lowercaseChar()?.takeIf { it in 'a'..'g' }?.toString() ?: "c"
        val afterLetter = rootPart.drop(1)
        val accidental = listOf("##", "bb", "#", "b").firstOrNull { afterLetter.startsWith(it) } ?: ""
        val octave = afterLetter.removePrefix(accidental).toIntOrNull() ?: 4

        Parsed(
            letter = letter,
            accidental = accidental,
            octave = octave,
            // Store mode with underscores (mini-notation safe)
            mode = modePart.ifBlank { "major" }.replace(" ", "_"),
        )
    }

    private data class Parsed(val letter: String, val accidental: String, val octave: Int, val mode: String)

    private var letter by value(parsed.letter)
    private var accidental by value(parsed.accidental)
    private var octave by value(parsed.octave)

    /** Mode is stored with "_" replacing spaces so it is safe as a mini-notation token. */
    private var mode by value(parsed.mode)

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"${letter}${accidental}${octave}:${mode}\""

    private fun currentScaleName(): String = "${letter}${accidental}${octave} ${mode.replace("_", " ")}"

    private fun currentScaleNotes(): List<String> = Scale.get(currentScaleName()).notes

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = currentValue != buildValue()

    /** Called after every field change in embedded mode — propagates live updates to the host. */
    private fun liveUpdate() {
        if (props.embedded) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() = props.toolCtx.onCancel()

    private fun onReset() {
        letter = parsed.letter
        accidental = parsed.accidental
        octave = parsed.octave
        mode = parsed.mode
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
                ui.small.header { +"Scale" }
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
    }

    private fun FlowContent.renderContent() {
        ui.form {
            ui.four.stackable.fields {

                // Note letter
                SelectField(letter, { letter = it; liveUpdate() }) {
                    label("Note")
                    letters.forEach { l ->
                        option(realValue = l) { +l.uppercase() }
                    }
                }

                // Accidental
                SelectField(accidental, { accidental = it; liveUpdate() }) {
                    label("Accidental")
                    accidentals.forEach { (value, label) ->
                        option(realValue = value, formValue = value) { +label }
                    }
                }

                // Octave
                UiInputField(octave, { octave = it; liveUpdate() }) {
                    label("Octave")
                    step(1)
                }

                // Scale mode — realValue uses "_" so mini-notation treats it as one token
                SelectField(mode, { mode = it; liveUpdate() }) {
                    label("Mode")
                    autoSuggest { search ->
                        val searchCleaned = search.trim()
                        flowOf(
                            modeNames
                                .filter { searchCleaned.isBlank() || it.contains(searchCleaned, ignoreCase = true) }
                                .map { m ->
                                    SelectFieldComponent.Option(
                                        realValue = m.replace(" ", "_"),
                                        formValue = m,
                                    ) { +m }
                                }
                        )
                    }
                }
            }
        }

        ui.divider {}

        // ── Staff notation ────────────────────────────────────────────────────
        val notes = currentScaleNotes()
        if (notes.isNotEmpty()) {
            NoteStaffComp(scaleName = currentScaleName(), range = (-notes.size)..notes.size)
        }

        ui.divider {}
    }
}
