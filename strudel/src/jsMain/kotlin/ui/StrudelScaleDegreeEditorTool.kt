package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.html.FlowContent
import kotlinx.html.Tag

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] for editing `n()` pattern strings (scale degree indices) on a treble-clef staff.
 *
 * Atom values are 0-based scale degree integers. Mapping to/from staff positions is done
 * via the active scale (default "C major"), which can be changed with the scale picker
 * rendered above the staff.
 */
object StrudelScaleDegreeEditorTool : KlangUiTool {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelScaleDegreeEditorComp(ctx)
    }
}

// ── Entry-point helper ────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelScaleDegreeEditorComp(toolCtx: KlangUiToolContext) =
    comp(StrudelScaleDegreeEditorComp.Props(toolCtx)) { StrudelScaleDegreeEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelScaleDegreeEditorComp(ctx: Ctx<Props>) : MnEditorBase<StrudelScaleDegreeEditorComp.Props>(ctx) {

    data class Props(override val toolCtx: KlangUiToolContext) : MnPatternEditorBase.BaseProps

    // ── Scale state ───────────────────────────────────────────────────────────

    private var scaleName by value("C major")

    private val scaleNotes: List<String>
        get() = Scale.get(scaleName).notes

    // ── Position mapping ──────────────────────────────────────────────────────

    override fun atomToStaffPosition(value: String): Int? {
        val degree = value.toIntOrNull() ?: return null
        val notes = scaleNotes
        if (notes.isEmpty()) return null
        return scaleDegreeToStaffPosition(degree, notes)
    }

    override fun staffPositionToAtomValue(pos: Int): String =
        nearestScaleDegree(pos, scaleNotes).toString()

    override fun keySignatureScaleName(): String = scaleName

    // ── Scale picker ──────────────────────────────────────────────────────────

    override fun FlowContent.renderExtraControls() {
        scalePicker(scaleName) { scaleName = it }
    }
}
