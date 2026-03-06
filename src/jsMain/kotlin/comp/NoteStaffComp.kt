package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe

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
        div {
            unsafe { raw(buildSvg()) }
        }
    }

    // ── SVG builder ───────────────────────────────────────────────────────────

    private fun buildSvg(): String {
        val cleanScale = props.scaleName.replace("_", " ").replace(":", " ")
        val stepsFn = Scale.steps(cleanScale)

        data class NI(val step: Int, val name: String, val pos: Int, val acc: String)

        val notes = props.range.mapNotNull { step ->
            val name = stepsFn(step).ifEmpty { return@mapNotNull null }
            val pos = staffPos(name) ?: return@mapNotNull null
            NI(step, name, pos, Note.get(name).pc.drop(1))
        }

        if (notes.isEmpty()) return ""

        // ── Layout ───────────────────────────────────────────────────────────

        val s = 7.0          // halfSp: pixels per staff-position step (half a staff space)
        val noteW = 40.0     // horizontal slot per note
        val noteRx = 6.5     // note-head horizontal radius
        val noteRy = 4.5     // note-head vertical radius
        val padL = 8.0       // small left margin; staff lines begin here
        val clefAreaW = 52.0 // width reserved for the clef glyphs on the staff
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

        val clefCx = padL + clefAreaW / 2   // centre of the clef area, on the staff

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $svgW $svgH" width="100%" style="display:block">""")

        val staffX1 = padL                   // staff lines start at left margin
        val staffX2 = svgW - padR            // staff lines end at right margin
        val notesX0 = padL + clefAreaW       // x origin for the first note column

        // ── Treble staff lines: E4(2) G4(4) B4(6) D5(8) F5(10) ───────────────

        for (pos in listOf(2, 4, 6, 8, 10)) {
            val ly = y(pos)
            sb.append("""<line x1="$staffX1" y1="$ly" x2="$staffX2" y2="$ly" stroke="#999" stroke-width="1"/>""")
        }

        // ── Bass staff lines: G2(-10) B2(-8) D3(-6) F3(-4) A3(-2) ────────────

        for (pos in listOf(-10, -8, -6, -4, -2)) {
            val ly = y(pos)
            sb.append("""<line x1="$staffX1" y1="$ly" x2="$staffX2" y2="$ly" stroke="#999" stroke-width="1"/>""")
        }

        // ── Vertical bar connecting both staves on the left ───────────────────

        sb.append("""<line x1="$staffX1" y1="${y(10)}" x2="$staffX1" y2="${y(-10)}" stroke="#777" stroke-width="2"/>""")

        // ── G clef (treble): loop wraps around G4 (pos=4, 2nd treble line) ────
        //
        //   Layout (all in halfSp units from the clef centre cx):
        //     • Stem:        x = cx + 1s,  from y(1) (below E4) to y(11) (above F5)
        //     • Oval loop:   cx′ = cx − 0.5s,  rx = 1.5s,  ry = 2s  (from E4 to B4)
        //     • Top curl:    from stem top sweeps right then back down to ~D5
        //     • Bottom scroll: from lower-left of loop curls right to ~C4

        sb.append(gClef(clefCx, s) { pos -> y(pos) })

        // ── F clef (bass): sits on F3 (pos=−4, 4th bass line) ────────────────
        //
        //   Layout:
        //     • Backward-C curve from A3 (pos=−2) to D3 (pos=−6), bowing left
        //     • Dot above F3: in space pos=−3  (between A3 and F3 lines)
        //     • Dot below F3: in space pos=−5  (between F3 and D3 lines)

        sb.append(fClef(clefCx, s) { pos -> y(pos) })

        // ── Notes ─────────────────────────────────────────────────────────────

        notes.forEachIndexed { i, note ->
            val cx = notesX0 + noteW * i + noteW / 2.0
            val cy = y(note.pos)
            val isRoot = note.step == 0

            // Ledger lines above treble staff (even positions ≥ 12)
            if (note.pos > 10) {
                val stop = if (note.pos % 2 == 0) note.pos else note.pos - 1
                var lp = 12
                while (lp <= stop) {
                    sb.append(ledgerLine(cx, y(lp), noteRx)); lp += 2
                }
            }

            // Ledger lines below bass staff (even positions ≤ −12)
            if (note.pos < -10) {
                val stop = if (note.pos % 2 == 0) note.pos else note.pos + 1
                var lp = -12
                while (lp >= stop) {
                    sb.append(ledgerLine(cx, y(lp), noteRx)); lp -= 2
                }
            }

            // Middle C ledger line (C4, pos=0) — in the gap between the two staves
            if (note.pos == 0) sb.append(ledgerLine(cx, y(0), noteRx))

            // Accidental glyph
            if (note.acc.isNotEmpty()) {
                val glyph = note.acc
                    .replace("##", "𝄪").replace("bb", "𝄫")
                    .replace("#", "♯").replace("b", "♭")
                sb.append("""<text x="${cx - noteRx - 2}" y="${cy + 4}" text-anchor="end" font-size="11" fill="#555">$glyph</text>""")
            }

            // Note head
            val fill = if (isRoot) "#1565c0" else "#2185d0"
            sb.append("""<ellipse cx="$cx" cy="$cy" rx="$noteRx" ry="$noteRy" fill="$fill"/>""")

            // Labels at bottom: note name (line 1) + step index (line 2)
            val labelFill = if (isRoot) "#1565c0" else "#999"
            sb.append("""<text x="$cx" y="${svgH - padB + 14}" text-anchor="middle" font-size="9" fill="$labelFill">${note.name}</text>""")
            sb.append("""<text x="$cx" y="${svgH - padB + 26}" text-anchor="middle" font-size="10" fill="$labelFill">${note.step}</text>""")
        }

        sb.append("</svg>")
        return sb.toString()
    }

    // ── G clef (treble clef) ──────────────────────────────────────────────────

    /**
     * Renders a G clef (treble clef) Unicode glyph (𝄞) centred at [cx].
     *
     * Per SMuFL convention the glyph anchor point is the G4 line (pos=4),
     * which maps to the SVG `y` baseline. Font-size = 8 * halfSp so that
     * 1 em = 4 staff-spaces, matching the SMuFL design grid.
     */
    private fun gClef(cx: Double, s: Double, y: (Int) -> Double): String =
        """<text x="$cx" y="${y(3)}" text-anchor="middle" font-size="${8 * s}" fill="#555">𝄞</text>"""

    // ── F clef (bass clef) ────────────────────────────────────────────────────

    /**
     * Renders an F clef (bass clef) Unicode glyph (𝄢) centred at [cx].
     *
     * Per SMuFL convention the glyph anchor point is the F3 line (pos=−4),
     * which maps to the SVG `y` baseline. Font-size = 8 * halfSp.
     */
    private fun fClef(cx: Double, s: Double, y: (Int) -> Double): String =
        """<text x="$cx" y="${y(-11) - 2}" text-anchor="middle" font-size="${8 * s}" fill="#555">𝄢</text>"""

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun ledgerLine(cx: Double, ly: Double, noteRx: Double) =
        """<line x1="${cx - noteRx - 4}" y1="$ly" x2="${cx + noteRx + 4}" y2="$ly" stroke="#999" stroke-width="1"/>"""
}

// ── File-level helpers ────────────────────────────────────────────────────────

private val diatonicOffset = mapOf('c' to 0, 'd' to 1, 'e' to 2, 'f' to 3, 'g' to 4, 'a' to 5, 'b' to 6)

/**
 * Staff position of [noteName] with C4 = 0.
 * Treble lines: 2(E4) 4(G4) 6(B4) 8(D5) 10(F5).
 * Bass lines: −10(G2) −8(B2) −6(D3) −4(F3) −2(A3).
 */
private fun staffPos(noteName: String): Int? {
    val note = Note.get(noteName)
    if (note.empty) return null
    val oct = note.oct ?: return null
    val letter = note.pc.firstOrNull()?.lowercaseChar() ?: return null
    return (oct - 4) * 7 + (diatonicOffset[letter] ?: return null)
}
