package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import kotlinx.browser.window
import kotlinx.css.Overflow
import kotlinx.css.UserSelect
import kotlinx.css.overflowX
import kotlinx.css.userSelect
import kotlinx.html.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Renders an interactive treble-clef staff SVG for editing note atoms and rests.
 *
 * Note heads are positioned according to [atomToPos]. Dragging a note head up or down
 * shifts it by diatonic steps and calls [onNodeChange] on release.
 * Clicking a rest symbol converts it to the default note at B4.
 * The [activeAtom] is highlighted with a blue stroke ring.
 */
fun FlowContent.noteStaffSvg(
    pattern: MnPattern?,
    activeAtom: MnNode.Atom?,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    scaleName: String? = null,
    structuralPattern: MnPattern? = null,
    onAtomSelect: (MnNode.Atom) -> Unit = {},
    onNodeChange: (MnNode, MnNode) -> Unit,
) {
    this@noteStaffSvg.NoteStaffComp(pattern, activeAtom, atomToPos, posToValue, scaleName, structuralPattern, onAtomSelect, onNodeChange)
}

@Suppress("FunctionName")
private fun Tag.NoteStaffComp(
    pattern: MnPattern?,
    activeAtom: MnNode.Atom?,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    scaleName: String?,
    structuralPattern: MnPattern?,
    onAtomSelect: (MnNode.Atom) -> Unit,
    onNodeChange: (MnNode, MnNode) -> Unit,
) = comp(
    NoteStaffComponent.Props(pattern, activeAtom, atomToPos, posToValue, scaleName, structuralPattern, onAtomSelect, onNodeChange)
) { NoteStaffComponent(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class NoteStaffComponent(ctx: Ctx<Props>) : Component<NoteStaffComponent.Props>(ctx) {

    // ── Layout constants ──────────────────────────────────────────────────────

    companion object {
        private const val HALF_STEP = 4.8       // px per diatonic step
        private const val NOTE_RADIUS_X = 4.8   // half-width of note head ellipse
        private const val NOTE_RADIUS_Y = 3.6   // half-height
        private const val LEFT_MARGIN = 38.0    // px reserved for clef (no key sig)
        private const val NOTE_COL_W = 22.0     // px per note column
        private const val BRACKET_COL_W = 10.0 // px per bracket mark column
        private const val STAFF_TOP = 29.0      // top of staff (top line Y)
        private const val CLEF_END_X = 30.0    // approx x where clef glyph ends
        private const val KEY_SIG_ACC_W = 9.0  // px per key-signature accidental
        private const val NOTE_GAP = 16.0      // gap between last key-sig acc and first note

        // Staff lines are at treble positions 2=E4, 4=G4, 6=B4, 8=D5, 10=F5
        private val STAFF_LINE_POSITIONS = listOf(2, 4, 6, 8, 10)

        /** Convert a staff position to SVG Y coordinate. C4=0, D4=1, … F5=10 is top line. */
        private fun staffPosToY(pos: Int, topY: Double): Double = topY + (10 - pos) * HALF_STEP

        /** Staff positions for sharps in treble clef (FCGDAEB order). */
        private val SHARP_KEY_POSITIONS = linkedMapOf(
            "F" to 10, "C" to 7, "G" to 11, "D" to 8, "A" to 5, "E" to 9, "B" to 6
        )

        /** Staff positions for flats in treble clef (BEADGCF order). */
        private val FLAT_KEY_POSITIONS = linkedMapOf(
            "B" to 6, "E" to 9, "A" to 5, "D" to 8, "G" to 4, "C" to 7, "F" to 10
        )

        enum class BracketType { Group, Alternation }

        sealed class LayoutItem {
            data class Note(val node: MnNode) : LayoutItem()
            data class BracketMark(val type: BracketType, val isOpen: Boolean) : LayoutItem()
        }

        private data class KeySignature(val symbol: String, val staffPositions: List<Int>)

        private fun buildKeySignature(scaleName: String?): KeySignature? {
            scaleName ?: return null
            val scale = Scale.get(scaleName)
            if (scale.empty) return null

            val sharpLetters = scale.notes
                .map { Note.get(it) }
                .filter { !it.empty && it.acc.contains('#') }
                .map { it.letter }
                .toSet()
            val flatLetters = scale.notes
                .map { Note.get(it) }
                .filter { !it.empty && it.acc.isNotEmpty() && !it.acc.contains('#') }
                .map { it.letter }
                .toSet()

            return when {
                sharpLetters.isNotEmpty() -> {
                    val positions = SHARP_KEY_POSITIONS.entries.filter { it.key in sharpLetters }.map { it.value }
                    if (positions.isEmpty()) null else KeySignature("♯", positions)
                }

                flatLetters.isNotEmpty() -> {
                    val positions = FLAT_KEY_POSITIONS.entries.filter { it.key in flatLetters }.map { it.value }
                    if (positions.isEmpty()) null else KeySignature("♭", positions)
                }

                else -> null
            }
        }
    }

    // ── Props ─────────────────────────────────────────────────────────────────

    data class Props(
        val pattern: MnPattern?,
        val activeAtom: MnNode.Atom?,
        val atomToPos: (String) -> Int?,
        val posToValue: (Int) -> String,
        val scaleName: String?,
        val structuralPattern: MnPattern?,
        val onAtomSelect: (MnNode.Atom) -> Unit,
        val onNodeChange: (MnNode, MnNode) -> Unit,
    )

    // ── Drag / click state ────────────────────────────────────────────────────

    private var dragAtomId: Int? = null
    private var dragStartY: Double = 0.0
    private var dragStartPos: Int = 0
    private var dragPreviewPos: Int? = null

    // ── Derived ───────────────────────────────────────────────────────────────

    private val staffItems: List<MnNode>
        get() = props.pattern?.let { p ->
            buildList { p.items.forEach { collectStaffNodes(it, this) } }
        } ?: emptyList()

    private fun collectStaffNodes(node: MnNode, items: MutableList<MnNode>) {
        when (node) {
            is MnNode.Atom -> items.add(node)
            is MnNode.Rest -> if (node.sourceRange != null) items.add(node)
            is MnNode.Group -> node.items.forEach { collectStaffNodes(it, items) }
            is MnNode.Alternation -> node.items.forEach { collectStaffNodes(it, items) }
            is MnNode.Stack -> node.layers.firstOrNull()?.forEach { collectStaffNodes(it, items) }
            is MnNode.Choice -> node.options.forEach { collectStaffNodes(it, items) }
            is MnNode.Repeat -> collectStaffNodes(node.node, items)
            is MnNode.Linebreak -> {}
        }
    }

    private fun buildLayoutItems(node: MnNode, result: MutableList<LayoutItem>) {
        when (node) {
            is MnNode.Atom -> result.add(LayoutItem.Note(node))
            is MnNode.Rest -> if (node.sourceRange != null) result.add(LayoutItem.Note(node))
            is MnNode.Group -> {
                result.add(LayoutItem.BracketMark(BracketType.Group, isOpen = true))
                node.items.forEach { buildLayoutItems(it, result) }
                result.add(LayoutItem.BracketMark(BracketType.Group, isOpen = false))
            }

            is MnNode.Alternation -> {
                result.add(LayoutItem.BracketMark(BracketType.Alternation, isOpen = true))
                node.items.forEach { buildLayoutItems(it, result) }
                result.add(LayoutItem.BracketMark(BracketType.Alternation, isOpen = false))
            }

            is MnNode.Stack -> node.layers.firstOrNull()?.forEach { buildLayoutItems(it, result) }
            is MnNode.Choice -> node.options.forEach { buildLayoutItems(it, result) }
            is MnNode.Repeat -> buildLayoutItems(node.node, result)
            is MnNode.Linebreak -> {}
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
        val atomId = dragAtomId ?: return@upHandler
        val delta = ((dragStartY - me.clientY) / HALF_STEP).roundToInt()
        val newPos = dragStartPos + delta
        val atom = staffItems.filterIsInstance<MnNode.Atom>().find { it.id == atomId }
        if (atom != null) {
            if (delta == 0) props.onAtomSelect(atom)
            else props.onNodeChange(atom, atom.copy(value = props.posToValue(newPos)))
        }
        dragAtomId = null
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
        val currentItems = staffItems

        div {
            css {
                overflowX = Overflow.auto
                userSelect = UserSelect.none
            }
            onMouseDown { e ->
                val element = e.target as? org.w3c.dom.Element
                val atomIdStr = element?.getAttribute("data-atom-id") ?: return@onMouseDown
                val posStr = element.getAttribute("data-staff-pos") ?: return@onMouseDown
                val atomId = atomIdStr.toIntOrNull() ?: return@onMouseDown
                val pos = posStr.toIntOrNull() ?: return@onMouseDown
                e.preventDefault()
                dragAtomId = atomId
                dragStartY = e.clientY.toDouble()
                dragStartPos = pos
                dragPreviewPos = pos
                window.addEventListener("mousemove", onMouseMoveWindow)
                window.addEventListener("mouseup", onMouseUpWindow)
                triggerRedraw()
            }

            // Double-click: convert note ↔ rest
            onDblClick { e ->
                val element = e.target as? org.w3c.dom.Element

                // Atom → rest
                val atomIdStr = element?.getAttribute("data-atom-id")
                if (atomIdStr != null) {
                    val atomId = atomIdStr.toIntOrNull() ?: return@onDblClick
                    val atom = currentItems.filterIsInstance<MnNode.Atom>()
                        .find { it.id == atomId } ?: return@onDblClick
                    props.onNodeChange(atom, MnNode.Rest(atom.sourceRange))
                    return@onDblClick
                }

                // Rest → note
                val restRangeStr = element?.getAttribute("data-rest-range-start")
                if (restRangeStr != null) {
                    val rangeStart = restRangeStr.toIntOrNull() ?: return@onDblClick
                    val rest = currentItems.filterIsInstance<MnNode.Rest>()
                        .find { it.sourceRange?.first == rangeStart } ?: return@onDblClick
                    props.onNodeChange(rest, MnNode.Atom(value = props.posToValue(6)))
                }
            }
            unsafe {
                +buildSvg()
            }
        }
    }

    // ── SVG builder ───────────────────────────────────────────────────────────

    private fun buildSvg(): String {
        // Key signature
        val keySig = buildKeySignature(props.scaleName)
        val keySigWidth = if (keySig != null) {
            keySig.staffPositions.size * KEY_SIG_ACC_W + NOTE_GAP
        } else {
            NOTE_GAP
        }
        val noteStartX = maxOf(LEFT_MARGIN, CLEF_END_X + keySigWidth)

        // Build interleaved layout: bracket marks get their own column alongside notes/rests
        val layoutItems = buildList {
            (props.structuralPattern ?: props.pattern)?.items?.forEach {
                buildLayoutItems(it, this)
            }
        }

        // Dynamic layout: expand height for notes outside normal staff range
        // Also account for key sig accidentals that may go above the top staff line (e.g. G5=11)
        val notePositions = layoutItems.filterIsInstance<LayoutItem.Note>()
            .mapNotNull { (it.node as? MnNode.Atom)?.let { a -> props.atomToPos(a.value) } }
        val keySigMaxPos = keySig?.staffPositions?.maxOrNull() ?: 10
        val maxPos = (notePositions.maxOrNull() ?: 10).coerceAtLeast(keySigMaxPos).coerceAtLeast(10)
        val minPos = (notePositions.minOrNull() ?: 0).coerceAtMost(0)
        val topExtra = maxOf(0.0, (maxPos - 10) * HALF_STEP + HALF_STEP * 1.5)
        val bottomExtra = maxOf(0.0, (-minPos) * HALF_STEP + HALF_STEP * 2)
        val topY = STAFF_TOP + topExtra
        val height = 104.0 + topExtra + bottomExtra

        val totalItemsWidth = layoutItems.sumOf { if (it is LayoutItem.Note) NOTE_COL_W else BRACKET_COL_W }
        val svgWidth = (noteStartX + totalItemsWidth + NOTE_COL_W).coerceAtLeast(160.0)

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${svgWidth.toInt()}" height="${height.toInt()}" style="display:block;font-family:serif;">""")

        // Brackets first (behind everything)
        var x = noteStartX
        for (item in layoutItems) {
            when (item) {
                is LayoutItem.Note -> x += NOTE_COL_W
                is LayoutItem.BracketMark -> {
                    renderBracketMark(sb, item, x + BRACKET_COL_W / 2, topY)
                    x += BRACKET_COL_W
                }
            }
        }

        // Staff lines
        val lineWidth = svgWidth - 8.0
        for (linePos in STAFF_LINE_POSITIONS) {
            val y = staffPosToY(linePos, topY)
            sb.append("""<line x1="4" y1="$y" x2="${lineWidth.toInt()}" y2="$y" stroke="#333" stroke-width="1.2"/>""")
        }

        // Treble clef
        sb.append(
            """<text x="4" y="${
                staffPosToY(
                    4,
                    topY
                ) + NOTE_RADIUS_Y * 4
            }" font-size="42" fill="#333" style="user-select:none;line-height:1">&#119070;</text>"""
        )

        // Key signature accidentals
        if (keySig != null) {
            keySig.staffPositions.forEachIndexed { idx, pos ->
                val x = CLEF_END_X + idx * KEY_SIG_ACC_W + KEY_SIG_ACC_W / 2
                val y = staffPosToY(pos, topY)
                sb.append("""<text x="$x" y="${y + 4}" text-anchor="middle" font-size="15" font-weight="bold" fill="#333" style="user-select:none">${keySig.symbol}</text>""")
            }
        }

        // Notes and rests (on top of staff lines and brackets)
        x = noteStartX
        for (item in layoutItems) {
            when (item) {
                is LayoutItem.Note -> {
                    val cx = x + NOTE_COL_W / 2
                    when (val node = item.node) {
                        is MnNode.Atom -> renderAtomSvg(sb, node, cx, topY)
                        is MnNode.Rest -> renderRestSvg(sb, node, cx, topY)
                        else -> {}
                    }
                    x += NOTE_COL_W
                }

                is LayoutItem.BracketMark -> x += BRACKET_COL_W
            }
        }

        sb.append("</svg>")
        return sb.toString()
    }

    private fun renderBracketMark(sb: StringBuilder, mark: LayoutItem.BracketMark, x: Double, topY: Double) {
        val ext = 4.0
        val yTop = topY - ext
        val yBottom = topY + 8 * HALF_STEP + ext
        val yMid = (yTop + yBottom) / 2
        val tickW = 5.0
        val sw = "2"
        val color = "#AAA"

        when (mark.type) {
            BracketType.Group -> {
                sb.append("""<line x1="$x" y1="$yTop" x2="$x" y2="$yBottom" stroke="$color" stroke-width="$sw"/>""")
                if (mark.isOpen) {
                    sb.append("""<line x1="$x" y1="$yTop"    x2="${x + tickW}" y2="$yTop"    stroke="$color" stroke-width="$sw"/>""")
                    sb.append("""<line x1="$x" y1="$yBottom" x2="${x + tickW}" y2="$yBottom" stroke="$color" stroke-width="$sw"/>""")
                } else {
                    sb.append("""<line x1="${x - tickW}" y1="$yTop"    x2="$x" y2="$yTop"    stroke="$color" stroke-width="$sw"/>""")
                    sb.append("""<line x1="${x - tickW}" y1="$yBottom" x2="$x" y2="$yBottom" stroke="$color" stroke-width="$sw"/>""")
                }
            }

            BracketType.Alternation -> {
                if (mark.isOpen) {
                    sb.append("""<line x1="${x + tickW}" y1="$yTop"    x2="$x"           y2="$yMid"    stroke="$color" stroke-width="$sw"/>""")
                    sb.append("""<line x1="$x"           y1="$yMid"    x2="${x + tickW}" y2="$yBottom" stroke="$color" stroke-width="$sw"/>""")
                } else {
                    sb.append("""<line x1="${x - tickW}" y1="$yTop"    x2="$x"           y2="$yMid"    stroke="$color" stroke-width="$sw"/>""")
                    sb.append("""<line x1="$x"           y1="$yMid"    x2="${x - tickW}" y2="$yBottom" stroke="$color" stroke-width="$sw"/>""")
                }
            }
        }
    }

    private fun renderRestSvg(sb: StringBuilder, rest: MnNode.Rest, x: Double, topY: Double) {
        val rangeStart = rest.sourceRange?.first ?: return
        // Draw a half-rest block: filled rectangle sitting on top of the B4 line (pos=6)
        val lineY = staffPosToY(6, topY)
        val w = NOTE_RADIUS_X * 2.4
        val h = HALF_STEP * 0.75
        sb.append(
            """<rect x="${x - w / 2}" y="${lineY - h}" width="$w" height="$h" rx="1" """ +
                    """fill="#444" data-rest-range-start="$rangeStart" """ +
                    """style="cursor:pointer" title="Double-click to convert to note"/>"""
        )
    }

    private fun renderAtomSvg(sb: StringBuilder, atom: MnNode.Atom, x: Double, topY: Double) {
        val isActive = atom.id == props.activeAtom?.id
        val isDragging = dragAtomId == atom.id
        val pos = if (isDragging) dragPreviewPos ?: dragStartPos else props.atomToPos(atom.value)

        if (pos == null) {
            renderUnknownSvg(sb, atom, x, isActive, topY)
            return
        }

        val y = staffPosToY(pos, topY)
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

        renderLedgerLines(sb, pos, x, topY)

        val stemUp = pos < 6
        if (stemUp) {
            val stemX = x + NOTE_RADIUS_X - 0.5
            val stemTop = y - HALF_STEP * 3.5
            sb.append("""<line x1="$stemX" y1="${y - NOTE_RADIUS_Y + 2}" x2="$stemX" y2="$stemTop" stroke="$noteColor" stroke-width="1.2"/>""")
        } else {
            val stemX = x - NOTE_RADIUS_X + 0.5
            val stemBottom = y + HALF_STEP * 3.5
            sb.append("""<line x1="$stemX" y1="${y + NOTE_RADIUS_Y - 2}" x2="$stemX" y2="$stemBottom" stroke="$noteColor" stroke-width="1.2"/>""")
        }

        sb.append(
            """<ellipse cx="$x" cy="$y" rx="$NOTE_RADIUS_X" ry="$NOTE_RADIUS_Y" """ +
                    """fill="$noteColor" stroke="$strokeColor" stroke-width="$strokeWidth" """ +
                    """data-atom-id="${atom.id}" data-staff-pos="$pos" style="cursor:grab"/>"""
        )

        renderAccidental(sb, atom.value, x, y)
    }

    private fun renderUnknownSvg(sb: StringBuilder, atom: MnNode.Atom, x: Double, isActive: Boolean, topY: Double) {
        val pos = 0 // C4
        val y = staffPosToY(pos, topY)
        renderLedgerLines(sb, pos, x, topY)
        val color = if (isActive) "#2266cc" else "#999"
        sb.append(
            """<text x="$x" y="${y + 3}" text-anchor="middle" font-size="11" fill="$color" """ +
                    """data-atom-id="${atom.id}" data-staff-pos="$pos" style="cursor:default;user-select:none">?</text>"""
        )
    }

    private fun renderLedgerLines(sb: StringBuilder, pos: Int, x: Double, topY: Double) {
        val ledgerHalfW = NOTE_RADIUS_X + 2.5
        // Below staff: pos <= 0, every even position gets a ledger line
        var p = 0
        while (p >= pos) {
            val y = staffPosToY(p, topY)
            sb.append("""<line x1="${x - ledgerHalfW}" y1="$y" x2="${x + ledgerHalfW}" y2="$y" stroke="#555" stroke-width="1.2"/>""")
            p -= 2
        }
        // Above staff: pos >= 12, every even position gets a ledger line
        p = 12
        while (p <= pos) {
            val y = staffPosToY(p, topY)
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
        val ax = x - NOTE_RADIUS_X - 6.0
        sb.append(
            """<text x="$ax" y="${y + 3}" text-anchor="middle" font-size="10" fill="#333" style="user-select:none">$accText</text>"""
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

// ── Event helpers ─────────────────────────────────────────────────────────────

private fun CommonAttributeGroupFacade.onDblClick(handler: (MouseEvent) -> Unit) {
    consumer.onTagEvent(this, "ondblclick", handler.asDynamic())
}
