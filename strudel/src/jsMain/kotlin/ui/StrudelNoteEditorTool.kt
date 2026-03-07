package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] for editing `note()` pattern strings on a treble-clef staff.
 *
 * Atom values are note names (e.g. "c4", "d#3"). The staff position is derived
 * directly from the note name; dragging a note head up/down changes it diatonically.
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

    data class Props(override val toolCtx: KlangUiToolContext) : MnEditorBase.BaseProps

    // ── Position mapping ──────────────────────────────────────────────────────

    override fun atomToStaffPosition(value: String): Int? {
        val note = Note.get(value)
        return note.staffPosition()
    }

    override fun staffPositionToAtomValue(pos: Int): String = staffPositionToNote(pos)
}
