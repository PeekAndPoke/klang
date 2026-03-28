package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.svgEllipse
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.klang.ui.svgText
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Renders a grand staff (G clef + F clef) showing notes of [scaleName] at the given step [range].
 * Step 0 is the root; positive steps ascend, negative steps descend.
 *
 * Staff positions with C4 = 0 (each step = one line-or-space):
 *   Treble lines: E4=2  G4=4  B4=6  D5=8  F5=10
 *   Bass lines:   G2=-10  B2=-8  D3=-6  F3=-4  A3=-2
 *   Middle C (C4=0) sits on a ledger line in the gap between the two staves.
 */
@Suppress("FunctionName")
fun Tag.NoteStaffComp(scaleName: String, range: IntRange) =
    comp(NoteStaffComp.Props(scaleName, range)) { NoteStaffComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

class NoteStaffComp(ctx: Ctx<Props>) : Component<NoteStaffComp.Props>(ctx) {

    data class Props(
        val scaleName: String,
        val range: IntRange,
    )

    override fun VDom.render() {
        div { renderSvg() }
    }

    // ── SVG rendering ─────────────────────────────────────────────────────────

    private fun FlowContent.renderSvg() {
        val cleanScale = props.scaleName.replace("_", " ").replace(":", " ")
        val stepsFn = Scale.steps(cleanScale)

        data class NI(val step: Int, val name: String, val pos: Int, val acc: String)

        val notes = props.range.mapNotNull { step ->
            val name = stepsFn(step).ifEmpty { return@mapNotNull null }
            val pos = staffPos(name) ?: return@mapNotNull null
            NI(step, name, pos, Note.get(name).pc.drop(1))
        }

        if (notes.isEmpty()) return

        // ── Layout ───────────────────────────────────────────────────────────

        val s = 4.8          // halfSp: pixels per staff-position step (half a staff space) — matches NoteStaffEditor HALF_STEP
        val noteW = 26.0     // horizontal slot per note — matches NoteStaffEditor NOTE_COL_W
        val noteRx = 4.8     // note-head horizontal radius — matches NoteStaffEditor NOTE_RADIUS_X
        val noteRy = 3.6     // note-head vertical radius — matches NoteStaffEditor NOTE_RADIUS_Y
        val padL = 8.0       // small left margin; staff lines begin here
        val clefAreaW = 38.0 // width reserved for the clef glyphs on the staff — matches NoteStaffEditor LEFT_MARGIN
        val padR = 16.0
        val padT = 16.0
        val padB = 34.0      // two-line labels (note name + step index)

        // Always encompass both full staves regardless of note range
        val minPos = minOf(notes.minOf { it.pos }, -10) - 2
        val maxPos = maxOf(notes.maxOf { it.pos }, 10) + 2

        // Y coordinate for a staff position (higher pitch → smaller y)
        fun y(pos: Int) = padT + (maxPos - pos) * s

        val svgW = padL + clefAreaW + noteW * notes.size + padR
        val svgH = y(minPos) + padB

        val staffX1 = padL                   // staff lines start at left margin
        val staffX2 = svgW - padR            // staff lines end at right margin
        val clefCx = padL + clefAreaW / 2   // centre of the clef area
        val notesX0 = padL + clefAreaW       // x origin for the first note column

        svgRoot(svgW.toInt(), svgH.toInt()) {

            // ── Treble staff lines: E4(2) G4(4) B4(6) D5(8) F5(10) ──────────

            for (pos in listOf(2, 4, 6, 8, 10)) {
                svgLine(staffX1, y(pos), staffX2, y(pos), stroke = "#555", strokeWidth = "1")
            }

            // ── Bass staff lines: G2(-10) B2(-8) D3(-6) F3(-4) A3(-2) ────────

            for (pos in listOf(-10, -8, -6, -4, -2)) {
                svgLine(staffX1, y(pos), staffX2, y(pos), stroke = "#555", strokeWidth = "1")
            }

            // ── Vertical bar connecting both staves on the left ───────────────

            svgLine(staffX1, y(10), staffX1, y(-10), stroke = "#666", strokeWidth = "2")

            // ── Clef glyphs ───────────────────────────────────────────────────

            renderGClef(clefCx, s) { y(it) }
            renderFClef(clefCx, s) { y(it) }

            // ── Notes ─────────────────────────────────────────────────────────

            notes.forEachIndexed { i, note ->
                val cx = notesX0 + noteW * i + noteW / 2.0
                val cy = y(note.pos)
                val isRoot = note.step == 0

                // Ledger lines above treble staff (even positions ≥ 12)
                if (note.pos > 10) {
                    val stop = if (note.pos % 2 == 0) note.pos else note.pos - 1
                    var lp = 12
                    while (lp <= stop) {
                        renderLedgerLine(cx, y(lp), noteRx); lp += 2
                    }
                }

                // Ledger lines below bass staff (even positions ≤ −12)
                if (note.pos < -10) {
                    val stop = if (note.pos % 2 == 0) note.pos else note.pos + 1
                    var lp = -12
                    while (lp >= stop) {
                        renderLedgerLine(cx, y(lp), noteRx); lp -= 2
                    }
                }

                // Middle C ledger line (C4, pos=0) — in the gap between the two staves
                if (note.pos == 0) renderLedgerLine(cx, y(0), noteRx)

                // Accidental glyph
                if (note.acc.isNotEmpty()) {
                    val glyph = note.acc
                        .replace("##", "\uD834\uDD2A").replace("bb", "\uD834\uDD2B") // 𝄪 𝄫
                        .replace("#", "♯").replace("b", "♭")
                    svgText(cx - noteRx - 2, cy + 4, text = glyph, fill = "#aaa", fontSize = "11", textAnchor = "end")
                }

                // Note head
                svgEllipse(cx, cy, noteRx, noteRy, fill = if (isRoot) "#e8b84b" else "#ddd")

                // Labels: note name (line 1) + step index (line 2)
                val labelFill = if (isRoot) "#e8b84b" else "#aaa"
                svgText(cx, svgH - padB + 14, text = note.name, fill = labelFill, fontSize = "9", textAnchor = "middle")
                svgText(cx, svgH - padB + 26, text = note.step.toString(), fill = labelFill, fontSize = "10", textAnchor = "middle")
            }
        }
    }

    // ── G clef (treble clef) ──────────────────────────────────────────────────

    /**
     * Renders a G clef (𝄞) centred at [cx].
     * Font-size = 8 × halfSp so 1 em = 4 staff-spaces (SMuFL grid).
     * Anchor point is G4 (pos=4); baseline adjusted to pos=3.
     */
    private fun FlowContent.renderGClef(cx: Double, s: Double, y: (Int) -> Double) {
        svgText(cx, y(3), text = "\uD834\uDD1E", fontSize = (8 * s).toString(), fill = "#aaa", textAnchor = "middle")
    }

    // ── F clef (bass clef) ────────────────────────────────────────────────────

    /**
     * Renders an F clef (𝄢) centred at [cx].
     * Font-size = 8 × halfSp. Baseline adjusted to pos=−11 − 2px.
     */
    private fun FlowContent.renderFClef(cx: Double, s: Double, y: (Int) -> Double) {
        svgText(cx, y(-11) - 2, text = "\uD834\uDD22", fontSize = (8 * s).toString(), fill = "#aaa", textAnchor = "middle")
    }

    // ── Ledger line ───────────────────────────────────────────────────────────

    private fun FlowContent.renderLedgerLine(cx: Double, ly: Double, noteRx: Double) {
        svgLine(cx - noteRx - 4, ly, cx + noteRx + 4, ly, stroke = "#555", strokeWidth = "1")
    }
}
