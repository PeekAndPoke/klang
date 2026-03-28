package io.peekandpoke.klang.sprudel.ui

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
 * A [KlangUiTool] for editing `n()` pattern strings (scale degree indices) on a treble-clef staff.
 *
 * Atom values are 0-based scale degree integers. Mapping to/from staff positions is done
 * via the active scale (default "C major"), which can be changed with the scale picker
 * rendered above the staff.
 */
object SprudelScaleDegreeEditorTool : KlangUiTool {
    override val title: String = "Scale Degree Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelScaleDegreeEditorComp(ctx)
    }
}

// ── Entry-point helper ────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelScaleDegreeEditorComp(toolCtx: KlangUiToolContext) =
    comp(SprudelScaleDegreeEditorComp.Props(toolCtx)) { SprudelScaleDegreeEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelScaleDegreeEditorComp(ctx: Ctx<Props>) : MnEditorBase<SprudelScaleDegreeEditorComp.Props>(ctx) {

    data class Props(override val toolCtx: KlangUiToolContext) : BaseProps

    // ── Scale state ───────────────────────────────────────────────────────────

    private var scaleName by value("C4 major")

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
        ui.divider()
        scalePicker(scaleName) { scaleName = it }
    }
}
