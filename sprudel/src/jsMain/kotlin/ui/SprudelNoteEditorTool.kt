package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] for editing `note()` pattern strings on a treble-clef staff.
 *
 * Atom values are note names (e.g. "c4", "d#3"). The staff position is derived
 * directly from the note name; dragging a note head up/down changes it according
 * to the active scale (auto-detected from the pattern or set manually).
 */
object SprudelNoteEditorTool : KlangUiTool {
    override val title: String = "Note Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelNoteEditorComp(ctx)
    }
}

// ── Entry-point helper ────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelNoteEditorComp(toolCtx: KlangUiToolContext) =
    comp(SprudelNoteEditorComp.Props(toolCtx)) { SprudelNoteEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelNoteEditorComp(ctx: Ctx<Props>) : MnEditorBase<SprudelNoteEditorComp.Props>(ctx) {

    data class Props(override val toolCtx: KlangUiToolContext) : BaseProps

    // ── Scale state ───────────────────────────────────────────────────────────

    /** Empty string means "auto-detect from pattern". */
    private var manualScaleName by value("")

    /** Scale name auto-detected from current pattern atoms. Empty if detection fails. */
    private val detectedScaleName: String
        get() {
            val atoms = pattern?.let { collectAtoms(it) } ?: return ""
            val noteNames = atoms.map { it.value }.filter { !Note.get(it).empty }
            if (noteNames.isEmpty()) return ""
            val scaleName = Scale.detect(noteNames).firstOrNull() ?: return ""
            // Inject the median octave from the actual notes so the scale's tonic
            // matches the octave range of the pattern (keeps the staff display tight).
            val octaves = noteNames.mapNotNull { Note.get(it).oct }
            if (octaves.isEmpty()) return scaleName
            val medianOct = octaves.sorted()[octaves.size / 2]
            val (tonic, type) = Scale.tokenize(scaleName)
            if (tonic.isEmpty()) return scaleName
            val tonicNote = Note.get(tonic)
            // Replace or add octave on the tonic
            return "${tonicNote.pc}$medianOct $type"
        }

    /** Effective scale: manual override (if valid) → auto-detected → "C chromatic". */
    private val activeScaleName: String
        get() {
            if (manualScaleName.isNotEmpty() && !Scale.get(manualScaleName).empty) return manualScaleName
            val detected = detectedScaleName
            if (detected.isNotEmpty()) return detected
            return "C4 chromatic"
        }

    // ── Position mapping ──────────────────────────────────────────────────────

    override fun atomToStaffPosition(value: String): Int? = Note.get(value).staffPosition()

    override fun staffPositionToAtomValue(pos: Int): String {
        val step = ((pos % 7) + 7) % 7
        val octave = 4 + pos.floorDiv(7)
        val letter = Note.stepToLetter(step)
        val scale = Scale.get(activeScaleName)
        val acc = if (!scale.empty) {
            scale.notes.find { Note.get(it).letter == letter }?.let { Note.get(it).acc } ?: ""
        } else {
            ""
        }
        return "${letter.lowercase()}$acc$octave"
    }

    override fun keySignatureScaleName(): String = activeScaleName

    // ── Scale picker ──────────────────────────────────────────────────────────

    override fun FlowContent.renderExtraControls() {
        ui.divider()
        scalePicker(activeScaleName) { manualScaleName = it }
    }
}
