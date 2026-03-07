package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import kotlinx.browser.window
import kotlinx.css.Overflow
import kotlinx.css.UserSelect
import kotlinx.css.overflowX
import kotlinx.css.userSelect
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Renders an interactive treble-clef staff SVG for editing note atoms.
 *
 * Note heads are positioned according to [atomToPos]. Dragging a note head up or down
 * shifts it by diatonic steps and calls [onAtomChange] on release.
 * The [activeAtom] is highlighted with a blue stroke ring.
 */
fun FlowContent.noteStaffSvg(
    pattern: MnPattern?,
    activeAtom: MnNode.Atom?,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    onAtomChange: (MnNode.Atom, MnNode.Atom) -> Unit,
) {
    NoteStaffComp(pattern, activeAtom, atomToPos, posToValue, onAtomChange)
}

@Suppress("FunctionName")
private fun Tag.NoteStaffComp(
    pattern: MnPattern?,
    activeAtom: MnNode.Atom?,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    onAtomChange: (MnNode.Atom, MnNode.Atom) -> Unit,
) = comp(
    NoteStaffComponent.Props(pattern, activeAtom, atomToPos, posToValue, onAtomChange)
) { NoteStaffComponent(it) }

// ── Staff layout constants ────────────────────────────────────────────────────

private const val HALF_STEP = 6.0       // px per diatonic step
private const val NOTE_RADIUS_X = 6.0   // half-width of note head ellipse
private const val NOTE_RADIUS_Y = 4.5   // half-height
private const val LEFT_MARGIN = 48.0    // px before first note
private const val NOTE_COL_W = 28.0     // px per note column
private const val STAFF_TOP = 36.0      // top of staff (top line Y)

// Staff lines are at treble positions 2=E4, 4=G4, 6=B4, 8=D5, 10=F5
private val STAFF_LINE_POSITIONS = listOf(2, 4, 6, 8, 10)

/** Convert a staff position to SVG Y coordinate. C4=0, D4=1, … F5=10 is top line. */
private fun staffPosToY(pos: Int): Double = STAFF_TOP + (10 - pos) * HALF_STEP

// ── Component ─────────────────────────────────────────────────────────────────

private class NoteStaffComponent(ctx: Ctx<Props>) : Component<NoteStaffComponent.Props>(ctx) {

    data class Props(
        val pattern: MnPattern?,
        val activeAtom: MnNode.Atom?,
        val atomToPos: (String) -> Int?,
        val posToValue: (Int) -> String,
        val onAtomChange: (MnNode.Atom, MnNode.Atom) -> Unit,
    )

    // ── Drag state ────────────────────────────────────────────────────────────

    private var dragAtomIdx: Int? = null
    private var dragStartY: Double = 0.0
    private var dragStartPos: Int = 0
    private var dragPreviewPos: Int? = null

    // ── Derived ───────────────────────────────────────────────────────────────

    private val atoms: List<MnNode.Atom>
        get() = props.pattern?.let { p ->
            buildList { p.items.forEach { collectStaffNodes(it, this) } }
        } ?: emptyList()

    // Collect all top-level renderable nodes (including Rests) in order
    private fun collectStaffNodes(node: MnNode, list: MutableList<MnNode.Atom>) {
        when (node) {
            is MnNode.Atom -> list.add(node)
            is MnNode.Group -> node.items.forEach { collectStaffNodes(it, list) }
            is MnNode.Alternation -> node.items.firstOrNull()?.let { collectStaffNodes(it, list) }
            is MnNode.Stack -> node.layers.firstOrNull()?.forEach { collectStaffNodes(it, list) }
            is MnNode.Choice -> node.options.forEach { collectStaffNodes(it, list) }
            is MnNode.Repeat -> collectStaffNodes(node.node, list)
            is MnNode.Rest -> {} // rests have no Atom; rendered separately via pattern walk
        }
    }

    // ── Window drag handlers ──────────────────────────────────────────────────

    private val onMouseMoveWindow: (Event) -> Unit = moveHandler@{ e ->
        val me = e as? MouseEvent ?: return@moveHandler
        val startPos = dragStartPos
        val delta = ((dragStartY - me.clientY) / HALF_STEP).roundToInt()
        dragPreviewPos = startPos + delta
        triggerRedraw()
    }

    private val onMouseUpWindow: (Event) -> Unit = upHandler@{ e ->
        val me = e as? MouseEvent ?: return@upHandler
        val idx = dragAtomIdx ?: return@upHandler
        val delta = ((dragStartY - me.clientY) / HALF_STEP).roundToInt()
        val newPos = dragStartPos + delta
        val atom = atoms.getOrNull(idx)
        if (atom != null) {
            props.onAtomChange(atom, atom.copy(value = props.posToValue(newPos)))
        }
        dragAtomIdx = null
        dragPreviewPos = null
        window.removeEventListener("mousemove", onMouseMoveWindow)
        window.removeEventListener("mouseup", onMouseUpWindow)
        triggerRedraw()
    }

    init {
        lifecycle.onUnmount {
            window.removeEventListener("mousemove", onMouseMoveWindow)
            window.removeEventListener("mouseup", onMouseUpWindow)
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        val currentAtoms = atoms
        val svgWidth = (LEFT_MARGIN + currentAtoms.size * NOTE_COL_W + NOTE_COL_W).coerceAtLeast(200.0)
        val svgHeight = 130.0

        div {
            css {
                overflowX = Overflow.auto
                userSelect = UserSelect.none
            }
            onMouseDown { e ->
                val target = e.target?.asDynamic()
                val idxStr = target?.dataset?.atomIdx as? String ?: return@onMouseDown
                val posStr = target.dataset.staffPos as? String ?: return@onMouseDown
                val idx = idxStr.toIntOrNull() ?: return@onMouseDown
                val pos = posStr.toIntOrNull() ?: return@onMouseDown
                e.preventDefault()
                dragAtomIdx = idx
                dragStartY = e.clientY.toDouble()
                dragStartPos = pos
                dragPreviewPos = pos
                window.addEventListener("mousemove", onMouseMoveWindow)
                window.addEventListener("mouseup", onMouseUpWindow)
                triggerRedraw()
            }
            unsafe {
                +buildSvg(currentAtoms, svgWidth, svgHeight)
            }
        }
    }

    // ── SVG builder ───────────────────────────────────────────────────────────

    private fun buildSvg(currentAtoms: List<MnNode.Atom>, width: Double, height: Double): String {
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${width.toInt()}" height="${height.toInt()}" style="display:block;font-family:serif;">""")

        // Staff lines
        val lineWidth = width - 8.0
        for (linePos in STAFF_LINE_POSITIONS) {
            val y = staffPosToY(linePos)
            sb.append("""<line x1="4" y1="$y" x2="${lineWidth.toInt()}" y2="$y" stroke="#333" stroke-width="1.2"/>""")
        }

        // Treble clef
        sb.append("""<text x="4" y="${staffPosToY(4) + NOTE_RADIUS_Y * 4}" font-size="52" fill="#333" style="user-select:none;line-height:1">&#119070;</text>""")

        // Note heads
        for ((idx, atom) in currentAtoms.withIndex()) {
            val x = LEFT_MARGIN + idx * NOTE_COL_W
            renderAtomSvg(sb, atom, idx, x)
        }

        sb.append("</svg>")
        return sb.toString()
    }

    private fun renderAtomSvg(sb: StringBuilder, atom: MnNode.Atom, idx: Int, x: Double) {
        val isActive = atom === props.activeAtom
        val isDragging = dragAtomIdx == idx
        val pos = if (isDragging) dragPreviewPos ?: dragStartPos else props.atomToPos(atom.value)

        if (pos == null) {
            // Unknown value — render a "?" at C4 position
            renderUnknownSvg(sb, atom, idx, x, isActive)
            return
        }

        val y = staffPosToY(pos)
        val noteColor = when {
            isDragging -> "#2266cc"
            isActive -> "#3355aa"
            else -> "#222"
        }
        val strokeColor = when {
            isActive || isDragging -> "#2266cc"
            else -> "#333"
        }
        val strokeWidth = if (isActive || isDragging) 2.0 else 1.2

        // Ledger lines (below staff: pos <= 0; above staff: pos >= 12)
        renderLedgerLines(sb, pos, x)

        // Stem (up for pos < 6, down for pos >= 6)
        val stemUp = pos < 6
        if (stemUp) {
            val stemX = x + NOTE_RADIUS_X - 0.5
            val stemTop = y - HALF_STEP * 3.5
            sb.append("""<line x1="$stemX" y1="${y - NOTE_RADIUS_Y + 0.5}" x2="$stemX" y2="$stemTop" stroke="$noteColor" stroke-width="1.5"/>""")
        } else {
            val stemX = x - NOTE_RADIUS_X + 0.5
            val stemBottom = y + HALF_STEP * 3.5
            sb.append("""<line x1="$stemX" y1="${y + NOTE_RADIUS_Y - 0.5}" x2="$stemX" y2="$stemBottom" stroke="$noteColor" stroke-width="1.5"/>""")
        }

        // Note head ellipse — data attributes for drag identification
        sb.append(
            """<ellipse cx="$x" cy="$y" rx="$NOTE_RADIUS_X" ry="$NOTE_RADIUS_Y" """ +
                    """fill="$noteColor" stroke="$strokeColor" stroke-width="$strokeWidth" """ +
                    """data-atom-idx="$idx" data-staff-pos="$pos" style="cursor:grab"/>"""
        )

        // Accidental text if needed
        renderAccidental(sb, atom.value, x, y)
    }

    private fun renderUnknownSvg(sb: StringBuilder, atom: MnNode.Atom, idx: Int, x: Double, isActive: Boolean) {
        val pos = 0 // C4
        val y = staffPosToY(pos)
        renderLedgerLines(sb, pos, x)
        val color = if (isActive) "#2266cc" else "#999"
        sb.append(
            """<text x="$x" y="${y + 4}" text-anchor="middle" font-size="14" fill="$color" """ +
                    """data-atom-idx="$idx" data-staff-pos="$pos" style="cursor:default;user-select:none">?</text>"""
        )
    }

    private fun renderLedgerLines(sb: StringBuilder, pos: Int, x: Double) {
        val ledgerHalfW = NOTE_RADIUS_X + 3.0
        // Below staff: pos <= 0, every even position gets a ledger line
        var p = 0
        while (p >= pos) {
            val y = staffPosToY(p)
            sb.append("""<line x1="${x - ledgerHalfW}" y1="$y" x2="${x + ledgerHalfW}" y2="$y" stroke="#555" stroke-width="1.2"/>""")
            p -= 2
        }
        // Above staff: pos >= 12, every even position gets a ledger line
        p = 12
        while (p <= pos) {
            val y = staffPosToY(p)
            sb.append("""<line x1="${x - ledgerHalfW}" y1="$y" x2="${x + ledgerHalfW}" y2="$y" stroke="#555" stroke-width="1.2"/>""")
            p += 2
        }
    }

    private fun renderAccidental(sb: StringBuilder, value: String, x: Double, y: Double) {
        // Detect sharps and flats in note names like "c#4", "db3", "f##5"
        val acc = extractAccidental(value) ?: return
        val accText = when {
            acc.startsWith("##") || acc.startsWith("x") -> "𝄪"
            acc.startsWith("#") -> "♯"
            acc.startsWith("bb") || acc.startsWith("ff") -> "𝄫"
            acc.startsWith("b") || acc.startsWith("f") -> "♭"
            else -> return
        }
        val ax = x - NOTE_RADIUS_X - 8.0
        sb.append(
            """<text x="$ax" y="${y + 4}" text-anchor="middle" font-size="12" fill="#333" style="user-select:none">$accText</text>"""
        )
    }

    private fun extractAccidental(value: String): String? {
        // value examples: "c4", "c#4", "db3", "f##5", "bb4", "2", "~"
        if (value.isEmpty() || value == "~") return null
        val firstChar = value[0].lowercaseChar()
        if (firstChar !in 'a'..'g') return null
        val rest = value.drop(1).takeWhile { it == '#' || it == 'b' || it == 'x' || it == 'f' || it == 's' }
        return rest.ifEmpty { null }
    }
}

// ── Staff position helpers ────────────────────────────────────────────────────

/**
 * Returns the staff position for this note (C4 = 0, D4 = 1, … B4 = 6, C5 = 7, …).
 * Returns null if the note is empty.
 */
fun io.peekandpoke.klang.tones.note.Note.staffPosition(): Int? {
    if (empty) return null
    return step + 7 * ((oct ?: 4) - 4)
}

/**
 * Converts a staff position back to a note name string (lowercase letter + octave).
 * C4 = 0, D4 = 1, E4 = 2, F4 = 3, G4 = 4, A4 = 5, B4 = 6, C5 = 7, …
 */
fun staffPositionToNote(pos: Int): String {
    val octave = 4 + pos.floorDiv(7)
    val step = ((pos % 7) + 7) % 7
    val letter = io.peekandpoke.klang.tones.note.Note.stepToLetter(step).lowercase()
    return "$letter$octave"
}

/**
 * Converts a scale degree (0-based) to a staff position given the scale's note list.
 *
 * The scale notes list contains pitch-class names with octave (e.g. ["C3","D3","E3","F3","G3","A3","B3"]).
 * Degrees outside [0, size) wrap around with octave adjustment.
 */
fun scaleDegreeToStaffPosition(degree: Int, scaleNotes: List<String>): Int {
    if (scaleNotes.isEmpty()) return 0
    val size = scaleNotes.size
    val idx = ((degree % size) + size) % size
    val octaveShift = degree.floorDiv(size)
    val note = io.peekandpoke.klang.tones.note.Note.get(scaleNotes[idx])
    return (note.staffPosition() ?: 0) + 7 * octaveShift
}

/**
 * Finds the scale degree whose staff position is closest to [targetPos].
 * Returns the degree (may be outside [0, size) for out-of-range positions).
 */
fun nearestScaleDegree(targetPos: Int, scaleNotes: List<String>): Int {
    if (scaleNotes.isEmpty()) return 0
    val size = scaleNotes.size

    // Search within a reasonable range of octaves
    var bestDegree = 0
    var bestDist = Int.MAX_VALUE
    for (octShift in -2..2) {
        for (i in 0 until size) {
            val deg = octShift * size + i
            val pos = scaleDegreeToStaffPosition(deg, scaleNotes)
            val dist = kotlin.math.abs(pos - targetPos)
            if (dist < bestDist) {
                bestDist = dist
                bestDegree = deg
            }
        }
    }
    return bestDegree
}
