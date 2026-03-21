package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.semanticui.forms.old.select.SelectField
import de.peekandpoke.kraft.semanticui.forms.old.select.SelectFieldComponent
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary
import kotlinx.coroutines.flow.flowOf
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Entry point ────────────────────────────────────────────────────────────────

/**
 * Renders a 4-field scale picker (note letter, accidental, octave, mode).
 *
 * [scaleName] is the currently active scale name (e.g. "D3 major"). Whenever any
 * field changes [onChange] is called with the new scale name in the same format.
 *
 * When [scaleName] changes externally (e.g. parent switches to auto-detected scale),
 * the form fields reset to match the new value.
 */
fun FlowContent.scalePicker(scaleName: String, onChange: (String) -> Unit) {
    this.ScalePickerComp(scaleName, onChange)
}

@Suppress("FunctionName")
private fun Tag.ScalePickerComp(scaleName: String, onChange: (String) -> Unit) =
    comp(ScalePickerComp.Props(scaleName, onChange)) { ScalePickerComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class ScalePickerComp(ctx: Ctx<Props>) : Component<ScalePickerComp.Props>(ctx) {

    data class Props(val scaleName: String, val onChange: (String) -> Unit)

    // ── Static data ───────────────────────────────────────────────────────────

    private val letters = listOf("c", "d", "e", "f", "g", "a", "b")

    private val accidentals = listOf(
        "" to "♮",
        "#" to "#",
        "##" to "##",
        "###" to "###",
        "b" to "b",
        "bb" to "bb",
        "bbb" to "bbb",
    )

    private val modeNames: List<String> = listOf("major", "minor", "pentatonic", "chromatic")
        .plus(ScaleTypeDictionary.all().flatMap { listOf(it.name) + it.aliases }.sorted())
        .distinct()

    // ── State ─────────────────────────────────────────────────────────────────

    private var letter by value(parseScaleName(props.scaleName).letter)
    private var accidental by value(parseScaleName(props.scaleName).accidental)
    private var octave by value(parseScaleName(props.scaleName).octave)
    private var mode by value(parseScaleName(props.scaleName).mode)

    init {
        lifecycle.onNextProps { new, old ->
            if (new.scaleName != old.scaleName) {
                val parsed = parseScaleName(new.scaleName)
                letter = parsed.letter
                accidental = parsed.accidental
                octave = parsed.octave
                mode = parsed.mode
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class Parsed(val letter: String, val accidental: String, val octave: Int, val mode: String)

    private fun parseScaleName(name: String): Parsed {
        val scale = Scale.get(name)
        return if (!scale.empty && scale.tonic != null) {
            val tokens = Note.tokenize(scale.tonic!!)
            Parsed(
                letter = tokens[0].lowercase().takeIf { it.isNotEmpty() } ?: "c",
                accidental = tokens[1],
                octave = tokens[2].toIntOrNull() ?: 4,
                mode = scale.type.replace(" ", "_"),
            )
        } else {
            Parsed("c", "", 4, "major")
        }
    }

    private fun emit() {
        props.onChange("$letter$accidental$octave ${mode.replace("_", " ")}")
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        ui.form {
            ui.four.stackable.fields {

                SelectField(letter, { letter = it; emit() }) {
                    label("Note")
                    letters.forEach { l -> option(realValue = l) { +l.uppercase() } }
                }

                UiInputField(octave, { octave = it; emit() }) {
                    label("Octave")
                    step(1)
                }

                SelectField(accidental, { accidental = it; emit() }) {
                    label("Accidental")
                    accidentals.forEach { (v, label) -> option(realValue = v, formValue = v) { +label } }
                }

                SelectField(mode, { mode = it; emit() }) {
                    label("Mode")
                    autoSuggest { search ->
                        val q = search.trim()
                        flowOf(
                            modeNames
                                .filter { q.isBlank() || it.contains(q, ignoreCase = true) }
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
    }
}
