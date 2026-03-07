package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onChange
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.css.*
import kotlinx.html.*

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

    data class Props(override val toolCtx: KlangUiToolContext) : MnEditorBase.BaseProps

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

    // ── Scale picker ──────────────────────────────────────────────────────────

    override fun FlowContent.renderExtraControls() {
        div {
            css {
                display = Display.flex
                alignItems = Align.center
                gap = 8.px
                marginTop = 8.px
                marginBottom = 4.px
            }
            span {
                css { fontSize = 12.px; color = Color("#666"); fontWeight = FontWeight.w600; minWidth = 60.px }
                +"Scale"
            }
            ui.small.input {
                input {
                    type = InputType.text
                    value = scaleName
                    placeholder = "e.g. C major"
                    css {
                        fontFamily = "monospace"
                        fontSize = 13.px
                    }
                    onChange { e ->
                        val v = e.target?.asDynamic()?.value as? String ?: return@onChange
                        val candidate = Scale.get(v)
                        if (!candidate.empty) {
                            scaleName = v
                        }
                    }
                }
            }
            val scale = Scale.get(scaleName)
            if (!scale.empty && scale.notes.isNotEmpty()) {
                span {
                    css { fontSize = 11.px; color = Color("#888"); fontFamily = "monospace" }
                    +scale.notes.joinToString(" ")
                }
            } else {
                span {
                    css { fontSize = 11.px; color = Color("#e03131") }
                    +"Unknown scale"
                }
            }
        }
    }
}
