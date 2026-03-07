package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
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
object StrudelNoteEditorTool : KlangUiTool {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelNoteEditorComp(ctx)
    }
}

// ── Entry-point helper ────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelNoteEditorComp(toolCtx: KlangUiToolContext) =
    comp(StrudelNoteEditorComp.Props(toolCtx)) { StrudelNoteEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelNoteEditorComp(ctx: Ctx<Props>) : MnEditorBase<StrudelNoteEditorComp.Props>(ctx) {

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
            return Scale.detect(noteNames).firstOrNull() ?: ""
        }

    /** Effective scale: manual override (if valid) → auto-detected → "C chromatic". */
    private val activeScaleName: String
        get() {
            if (manualScaleName.isNotEmpty() && !Scale.get(manualScaleName).empty) return manualScaleName
            val detected = detectedScaleName
            if (detected.isNotEmpty()) return detected
            return "C chromatic"
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
        } else ""
        return "${letter.lowercase()}$acc$octave"
    }

    // ── Scale picker ──────────────────────────────────────────────────────────

    override fun FlowContent.renderExtraControls() {
        ui.divider()
        scalePicker(activeScaleName) { manualScaleName = it }
    }
}
