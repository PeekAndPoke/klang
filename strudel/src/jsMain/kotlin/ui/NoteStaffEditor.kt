package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import io.peekandpoke.klang.ui.*
import kotlinx.browser.window
import kotlinx.css.Overflow
import kotlinx.css.UserSelect
import kotlinx.css.overflowX
import kotlinx.css.userSelect
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Renders an interactive treble-clef staff SVG for editing note atoms and rests.
 *
 * - Drag a note head up/down to change pitch; emits [NoteStaffEditor.Action.Replace].
 * - Double-click a note or rest to delete it; emits [NoteStaffEditor.Action.Remove].
 * - Double-click empty staff area to insert a note; emits [NoteStaffEditor.Action.InsertBetween] or [NoteStaffEditor.Action.InsertAt].
 * - Right-click a note/rest to toggle note ↔ rest; emits [NoteStaffEditor.Action.Replace].
 * - Single-click a note or rest to select it; emits [NoteStaffEditor.Action.Select].
 * - The [selection] node is highlighted with a blue stroke.
 */
internal fun FlowContent.noteStaffSvg(
    /** Flat pattern used to determine which atoms/rests to display. */
    pattern: MnPattern?,
    /** Maps a raw atom value (e.g. "c4") to a staff position (C4=0, D4=1, …), or null if not renderable. */
    atomToPos: (String) -> Int?,
    /** Maps a staff position back to a raw atom value string (e.g. 6 → "b4"). */
    posToValue: (Int) -> String,
    /** Optional scale name used to draw a key signature on the staff. */
    scaleName: String? = null,
    /** Hierarchical pattern used for bracket structure; falls back to [pattern] if null. */
    structuralPattern: MnPattern? = null,
    /** Currently selected atom or rest — highlighted with a blue stroke. */
    selection: MnSelection? = null,
    /** Receives all user interactions with the staff. */
    onAction: (NoteStaffEditor.Action) -> Unit = {},
) {
    this@noteStaffSvg.NoteStaffComp(
        pattern, atomToPos, posToValue, scaleName, structuralPattern, selection, onAction,
    )
}

@Suppress("FunctionName")
private fun Tag.NoteStaffComp(
    pattern: MnPattern?,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    scaleName: String?,
    structuralPattern: MnPattern?,
    selection: MnSelection?,
    onAction: (NoteStaffEditor.Action) -> Unit,
) = comp(
    NoteStaffEditor.Props(
        pattern, atomToPos, posToValue, scaleName, structuralPattern, selection, onAction,
    )
) { NoteStaffEditor(it) }

// ── Component ─────────────────────────────────────────────────────────────────

internal class NoteStaffEditor(ctx: Ctx<Props>) : Component<NoteStaffEditor.Props>(ctx) {

    // ── Action type ───────────────────────────────────────────────────────────

    /** Actions emitted by the note staff editor. */
    sealed interface Action {
        /** User clicked a note or rest to select it. */
        data class Select(val selection: MnSelection) : Action

        /** User double-clicked a note or rest to delete it. */
        data class Remove(val node: MnNode) : Action

        /**
         * User double-clicked between two nodes to insert a new note there.
         * [leftNode] is the node to the left (null = insert at the very start).
         * [rightNode] is the node to the right (null = insert at the very end).
         */
        data class InsertBetween(val leftNode: MnNode?, val rightNode: MnNode?, val staffPos: Int) : Action

        /**
         * User double-clicked on top of an existing node to add a note at the same position.
         * If [existingNode] is an [MnNode.Atom] → wrap in a Stack `[existingAtom, newAtom]`.
         * If [existingNode] is an [MnNode.Stack] → add [staffPos] to that stack.
         */
        data class InsertAt(val existingNode: MnNode, val staffPos: Int) : Action

        /** User dragged a note to a new pitch, or right-clicked to toggle note ↔ rest. */
        data class Replace(val old: MnNode, val new: MnNode) : Action
    }

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
            data class Stack(val items: List<MnNode>) : LayoutItem()
            data class BracketMark(val type: BracketType, val isOpen: Boolean) : LayoutItem()
        }

        /** What happens when the user double-clicks at this snap slot. */
        sealed interface InsertTarget {
            /** Insert a new note between [leftNode] and [rightNode] (either may be null = boundary). */
            data class Between(val leftNode: MnNode?, val rightNode: MnNode?) : InsertTarget

            /** Overlay an existing column — add note to the stack at [node]. */
            data class At(val node: MnNode) : InsertTarget
        }

        /**
         * A snap slot in the staff.
         * [x] = snap x; [target] = what to do on double-click;
         * [layoutItemIdx] = index into layoutItems;
         * [isPush] = true → ghost creates a gap (sequential insert),
         * false → ghost overlays the column (stack insert).
         */
        data class BoundarySlot(
            val x: Double,
            val target: InsertTarget,
            val layoutItemIdx: Int,
            val isPush: Boolean,
        )

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
        /** Flat pattern used to determine which atoms/rests to display. */
        val pattern: MnPattern?,
        /** Maps a raw atom value (e.g. "c4") to a staff position (C4=0, D4=1, …). */
        val atomToPos: (String) -> Int?,
        /** Maps a staff position back to a raw atom value string. */
        val posToValue: (Int) -> String,
        /** Optional scale name used to draw a key signature. */
        val scaleName: String?,
        /** Hierarchical pattern used for bracket structure. */
        val structuralPattern: MnPattern?,
        /** Currently selected atom or rest — highlighted with a blue stroke. */
        val selection: MnSelection?,
        /** Receives all user interactions with the staff. */
        val onAction: (Action) -> Unit,
    )

    // ── Drag / click state ────────────────────────────────────────────────────

    private var dragAtomId: Int? = null
    private var dragStartY: Double = 0.0
    private var dragStartPos: Int = 0
    private var dragPreviewPos: Int? = null

    // ── Layout snapshot (updated each render, used for click-to-insert) ────────

    /** topY of the last rendered staff, used to map click Y → staff position. */
    private var lastTopY: Double = STAFF_TOP

    /** (atomId, svgX-center) for each note atom in the last rendered layout, in order. */
    private var lastAtomCenters: List<Pair<Int, Double>> = emptyList()

    /** Snap slots for ghost-note; index into this list is stored in [ghostInsertIdx]. */
    private var lastBoundaries: List<BoundarySlot> = emptyList()

    // ── Ghost note state ──────────────────────────────────────────────────────

    /** Index into lastBoundaries where the ghost note will be inserted (null = no ghost). */
    private var ghostInsertIdx: Int? = null

    /** Snapped staff position under the mouse cursor. */
    private var ghostStaffPos: Int? = null

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
            is MnNode.Stack -> node.layers.forEach { layer -> layer.forEach { collectStaffNodes(it, items) } }
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

            is MnNode.Stack -> {
                val stackNodes = buildList {
                    node.layers.forEach { layer -> layer.forEach { collectStaffNodes(it, this) } }
                }
                if (stackNodes.isNotEmpty()) result.add(LayoutItem.Stack(stackNodes))
            }
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
            if (delta == 0) props.onAction(Action.Select(MnSelection.Atom(atom)))
            else props.onAction(Action.Replace(atom, atom.copy(value = props.posToValue(newPos))))
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
        div {
            css {
                overflowX = Overflow.auto
                userSelect = UserSelect.none
            }
            onMouseDown(::handleMouseDown)
            onDblClick(::handleDblClick)
            onContextMenu(::handleContextMenu)
            onMouseMove(::handleMouseMove)
            onMouseLeave { handleMouseLeave() }
            renderSvg()
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private fun handleMouseDown(e: MouseEvent) {
        val element = e.target as? org.w3c.dom.Element

        // Rest click → select
        val restEl = element?.closest("[data-rest-range-start]")
        if (restEl != null) {
            val rangeStart = restEl.getAttribute("data-rest-range-start")?.toIntOrNull() ?: return
            val rest = staffItems.filterIsInstance<MnNode.Rest>()
                .find { it.sourceRange?.first == rangeStart } ?: return
            props.onAction(Action.Select(MnSelection.Rest(rest)))
            return
        }

        // Note click → start drag (select emitted on mouseup when delta == 0)
        val noteEl = element?.closest("[data-atom-id]") ?: return
        val atomId = noteEl.getAttribute("data-atom-id")?.toIntOrNull() ?: return
        val pos = noteEl.getAttribute("data-staff-pos")?.toIntOrNull() ?: return

        e.preventDefault()
        dragAtomId = atomId
        dragStartY = e.clientY.toDouble()
        dragStartPos = pos
        dragPreviewPos = pos
        window.addEventListener("mousemove", onMouseMoveWindow)
        window.addEventListener("mouseup", onMouseUpWindow)
        triggerRedraw()
    }

    // Double-click: remove atom/rest, or insert on empty area.
    // The VDom DSL keeps DOM nodes stable across redraws (keyed elements),
    // so the native dblclick event fires reliably.
    private fun handleDblClick(e: MouseEvent) {
        val element = e.target as? org.w3c.dom.Element

        // Double-click on atom → remove
        val noteEl = element?.closest("[data-atom-id]")
        if (noteEl != null) {
            val atomId = noteEl.getAttribute("data-atom-id")?.toIntOrNull() ?: return
            val atom = staffItems.filterIsInstance<MnNode.Atom>()
                .find { it.id == atomId } ?: return
            props.onAction(Action.Remove(atom))
            return
        }

        // Double-click on rest → remove
        val restEl = element?.closest("[data-rest-range-start]")
        if (restEl != null) {
            val rangeStart = restEl.getAttribute("data-rest-range-start")?.toIntOrNull() ?: return
            val rest = staffItems.filterIsInstance<MnNode.Rest>()
                .find { it.sourceRange?.first == rangeStart } ?: return
            props.onAction(Action.Remove(rest))
            return
        }

        // Double-click on empty staff area → use ghost slot (already snapped to nearest boundary)
        val gIdx = ghostInsertIdx
        val gPos = ghostStaffPos
        val slot = gIdx?.let { lastBoundaries.getOrNull(it) } ?: return
        when (val target = slot.target) {
            is InsertTarget.Between -> props.onAction(Action.InsertBetween(target.leftNode, target.rightNode, gPos ?: 6))
            is InsertTarget.At -> props.onAction(Action.InsertAt(target.node, gPos ?: 6))
        }
    }

    // Right-click: toggle note ↔ rest
    private fun handleContextMenu(e: MouseEvent) {
        e.preventDefault()
        val element = e.target as? org.w3c.dom.Element

        // Note → rest
        val noteEl = element?.closest("[data-atom-id]")
        if (noteEl != null) {
            val atomId = noteEl.getAttribute("data-atom-id")?.toIntOrNull() ?: return
            val atom = staffItems.filterIsInstance<MnNode.Atom>()
                .find { it.id == atomId } ?: return
            props.onAction(Action.Replace(atom, MnNode.Rest(atom.sourceRange)))
            return
        }

        // Rest → note (at the clicked pitch)
        val restEl = element?.closest("[data-rest-range-start]") ?: return
        val rangeStart = restEl.getAttribute("data-rest-range-start")?.toIntOrNull() ?: return
        val rest = staffItems.filterIsInstance<MnNode.Rest>()
            .find { it.sourceRange?.first == rangeStart } ?: return
        val svg = restEl.closest("svg")
        val staffPos = if (svg != null) {
            val rect = svg.getBoundingClientRect()
            ((lastTopY + 10 * HALF_STEP - (e.clientY - rect.top)) / HALF_STEP).roundToInt()
        } else 6
        props.onAction(Action.Replace(rest, MnNode.Atom(value = props.posToValue(staffPos))))
    }

    // Mouse move over staff area: snap ghost to nearest column boundary edge
    private fun handleMouseMove(e: MouseEvent) {
        if (dragAtomId != null) return
        val element = e.target as? org.w3c.dom.Element
        val svg = element?.closest("svg")
        if (svg == null) {
            if (ghostInsertIdx != null) {
                ghostInsertIdx = null; ghostStaffPos = null; triggerRedraw()
            }
            return
        }
        val rect = svg.getBoundingClientRect()
        val svgX = e.clientX - rect.left
        val newIdx = lastBoundaries.indices.minByOrNull { i ->
            kotlin.math.abs(lastBoundaries[i].x - svgX)
        } ?: 0
        val newPos = ((lastTopY + 10 * HALF_STEP - (e.clientY - rect.top)) / HALF_STEP).roundToInt()
        if (newIdx != ghostInsertIdx || newPos != ghostStaffPos) {
            ghostInsertIdx = newIdx; ghostStaffPos = newPos; triggerRedraw()
        }
    }

    private fun handleMouseLeave() {
        if (ghostInsertIdx != null) {
            ghostInsertIdx = null; ghostStaffPos = null; triggerRedraw()
        }
    }

    // ── SVG DSL rendering ─────────────────────────────────────────────────────

    private fun FlowContent.renderSvg() {
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
        val notePositions = layoutItems.flatMap { item ->
            when (item) {
                is LayoutItem.Note -> listOfNotNull((item.node as? MnNode.Atom)?.let { props.atomToPos(it.value) })
                is LayoutItem.Stack -> item.items.filterIsInstance<MnNode.Atom>().mapNotNull { props.atomToPos(it.value) }
                is LayoutItem.BracketMark -> emptyList()
            }
        }
        val keySigMaxPos = keySig?.staffPositions?.maxOrNull() ?: 10
        val maxPos = (notePositions.maxOrNull() ?: 10).coerceAtLeast(keySigMaxPos).coerceAtLeast(10)
        val minPos = (notePositions.minOrNull() ?: 0).coerceAtMost(0)
        val topExtra = maxOf(0.0, (maxPos - 10) * HALF_STEP + HALF_STEP * 1.5)
        val bottomExtra = maxOf(0.0, (-minPos) * HALF_STEP + HALF_STEP * 2)
        val topY = STAFF_TOP + topExtra
        val height = 104.0 + topExtra + bottomExtra

        val totalItemsWidth = layoutItems.sumOf {
            when (it) {
                is LayoutItem.Note, is LayoutItem.Stack -> NOTE_COL_W
                is LayoutItem.BracketMark -> BRACKET_COL_W
            }
        }
        val ghostSlot = ghostInsertIdx?.let { lastBoundaries.getOrNull(it) }
        val svgWidth = (noteStartX + totalItemsWidth + NOTE_COL_W +
                (if (ghostSlot?.isPush == true) NOTE_COL_W else 0.0)).coerceAtLeast(160.0)

        val atomCenters = mutableListOf<Pair<Int, Double>>()

        svgRoot(svgWidth.toInt(), height.toInt(), style = "display:block;font-family:serif;") {

            // Bracket marks (behind everything); push-slots shift items at layoutItemIdx right
            var x = noteStartX
            for ((idx, item) in layoutItems.withIndex()) {
                if (ghostSlot?.isPush == true && ghostSlot.layoutItemIdx == idx) x += NOTE_COL_W
                when (item) {
                    is LayoutItem.Note, is LayoutItem.Stack -> x += NOTE_COL_W
                    is LayoutItem.BracketMark -> {
                        renderBracketMark(item, x + BRACKET_COL_W / 2, topY)
                        x += BRACKET_COL_W
                    }
                }
            }

            // Staff lines
            val lineWidth = svgWidth - 8.0
            for ((idx, linePos) in STAFF_LINE_POSITIONS.withIndex()) {
                val y = staffPosToY(linePos, topY)
                svgLine(4, y, lineWidth.toInt(), y, key = "staff-$idx")
            }

            // Treble clef
            svgText(
                4, staffPosToY(4, topY) + NOTE_RADIUS_Y * 4,
                text = "\uD834\uDD1E", // 𝄞
                fontSize = "42",
                style = "user-select:none;line-height:1",
                key = "clef",
            )

            // Key signature accidentals
            keySig?.staffPositions?.forEachIndexed { idx, pos ->
                val ax = CLEF_END_X + idx * KEY_SIG_ACC_W + KEY_SIG_ACC_W / 2
                val ay = staffPosToY(pos, topY)
                svgText(
                    ax, ay + 4, text = keySig.symbol,
                    fontSize = "15", fontWeight = "bold",
                    textAnchor = "middle",
                    style = "user-select:none",
                    key = "keysig-$idx",
                )
            }

            // Notes, rests, and ghost preview.
            // Push-slots shift items right; overlay-slots draw ghost on top without shifting.
            x = noteStartX
            for ((idx, item) in layoutItems.withIndex()) {
                // Push ghost: insert gap before this item
                if (ghostSlot?.isPush == true && ghostSlot.layoutItemIdx == idx) {
                    renderGhostNote(x + NOTE_COL_W / 2, ghostStaffPos, topY)
                    x += NOTE_COL_W
                }
                when (item) {
                    is LayoutItem.Note -> {
                        val cx = x + NOTE_COL_W / 2
                        // Overlay ghost: draw ghost on top of this column (stack indicator)
                        if (ghostSlot?.isPush == false && ghostSlot.layoutItemIdx == idx) {
                            renderGhostNote(cx, ghostStaffPos, topY)
                        }
                        when (val node = item.node) {
                            is MnNode.Atom -> {
                                renderAtomSvg(node, cx, topY)
                                atomCenters.add(node.id to cx)
                            }

                            is MnNode.Rest -> renderRestSvg(
                                node, cx, topY,
                                node.sourceRange == props.selection.rest?.sourceRange,
                            )

                            else -> {}
                        }
                        x += NOTE_COL_W
                    }

                    is LayoutItem.Stack -> {
                        val cx = x + NOTE_COL_W / 2
                        // Overlay ghost: draw ghost on top of this column (stack indicator)
                        if (ghostSlot?.isPush == false && ghostSlot.layoutItemIdx == idx) {
                            renderGhostNote(cx, ghostStaffPos, topY)
                        }
                        for (node in item.items) {
                            when (node) {
                                is MnNode.Atom -> {
                                    renderAtomSvg(node, cx, topY)
                                    atomCenters.add(node.id to cx)
                                }

                                is MnNode.Rest -> renderRestSvg(
                                    node, cx, topY,
                                    node.sourceRange == props.selection.rest?.sourceRange,
                                )

                                else -> {}
                            }
                        }
                        x += NOTE_COL_W
                    }

                    is LayoutItem.BracketMark -> x += BRACKET_COL_W
                }
            }
            // Push ghost after all items
            if (ghostSlot?.isPush == true && ghostSlot.layoutItemIdx == layoutItems.size) {
                renderGhostNote(x + NOTE_COL_W / 2, ghostStaffPos, topY)
            }
        }

        lastTopY = topY
        lastAtomCenters = atomCenters
        // Precompute first renderable node at-or-after each layout index (for right-neighbour lookup).
        val firstNodeAfter = arrayOfNulls<MnNode>(layoutItems.size + 1)
        var lookahead: MnNode? = null
        for (i in layoutItems.indices.reversed()) {
            lookahead = when (val li = layoutItems[i]) {
                is LayoutItem.Note -> li.node
                is LayoutItem.Stack -> li.items.firstOrNull()
                is LayoutItem.BracketMark -> lookahead
            }
            firstNodeAfter[i] = lookahead
        }

        // Snap slots: push-edge before each item + overlay-center for Note/Stack + push-edge at end.
        lastBoundaries = buildList {
            var bx = noteStartX
            var leftNode: MnNode? = null
            for ((idx, item) in layoutItems.withIndex()) {
                val itemW = when (item) {
                    is LayoutItem.Note, is LayoutItem.Stack -> NOTE_COL_W
                    is LayoutItem.BracketMark -> BRACKET_COL_W
                }
                // Push-edge before this item
                add(BoundarySlot(bx, InsertTarget.Between(leftNode, firstNodeAfter[idx]), idx, isPush = true))
                // Overlay-center for notes/stacks — stack onto existing note
                when (item) {
                    is LayoutItem.Note -> {
                        add(BoundarySlot(bx + itemW / 2, InsertTarget.At(item.node), idx, isPush = false))
                        leftNode = item.node
                    }
                    is LayoutItem.Stack -> {
                        add(
                            BoundarySlot(
                                bx + itemW / 2,
                                InsertTarget.At(item.items.firstOrNull() ?: item.items.first()),
                                idx,
                                isPush = false
                            )
                        )
                        leftNode = item.items.lastOrNull()
                    }

                    is LayoutItem.BracketMark -> {}
                }
                bx += itemW
            }
            // Push-edge after all items
            add(BoundarySlot(bx, InsertTarget.Between(leftNode, null), layoutItems.size, isPush = true))
        }
    }

    private fun FlowContent.renderGhostNote(cx: Double, staffPos: Int?, topY: Double) {
        if (staffPos == null) return
        val gy = staffPosToY(staffPos, topY)
        renderLedgerLines(staffPos, cx, topY)
        svgEllipse(cx, gy, NOTE_RADIUS_X, NOTE_RADIUS_Y, fill = "#2266cc", opacity = "0.35", style = "pointer-events:none")
    }

    private fun FlowContent.renderBracketMark(mark: LayoutItem.BracketMark, x: Double, topY: Double) {
        val ext = 4.0
        val yTop = topY - ext
        val yBottom = topY + 8 * HALF_STEP + ext
        val yMid = (yTop + yBottom) / 2
        val tickW = 5.0
        val sw = "2"
        val color = "#AAA"

        when (mark.type) {
            BracketType.Group -> {
                svgLine(x, yTop, x, yBottom, stroke = color, strokeWidth = sw)
                if (mark.isOpen) {
                    svgLine(x, yTop, x + tickW, yTop, stroke = color, strokeWidth = sw)
                    svgLine(x, yBottom, x + tickW, yBottom, stroke = color, strokeWidth = sw)
                } else {
                    svgLine(x - tickW, yTop, x, yTop, stroke = color, strokeWidth = sw)
                    svgLine(x - tickW, yBottom, x, yBottom, stroke = color, strokeWidth = sw)
                }
            }

            BracketType.Alternation -> {
                if (mark.isOpen) {
                    svgLine(x + tickW, yTop, x, yMid, stroke = color, strokeWidth = sw)
                    svgLine(x, yMid, x + tickW, yBottom, stroke = color, strokeWidth = sw)
                } else {
                    svgLine(x - tickW, yTop, x, yMid, stroke = color, strokeWidth = sw)
                    svgLine(x, yMid, x - tickW, yBottom, stroke = color, strokeWidth = sw)
                }
            }
        }
    }

    private fun FlowContent.renderRestSvg(
        rest: MnNode.Rest, x: Double, topY: Double, isActive: Boolean = false,
    ) {
        val rangeStart = rest.sourceRange?.first ?: return
        val lineY = staffPosToY(6, topY)
        val w = NOTE_RADIUS_X * 2.4
        val h = HALF_STEP * 0.75
        svgG(key = "rest-$rangeStart", style = "cursor:default") {
            attributes["data-rest-range-start"] = rangeStart.toString()
            svgRect(
                x - w / 2, lineY - h, w, h,
                fill = if (isActive) "#3355aa" else "#444",
                rx = "1",
                stroke = if (isActive) "#2266cc" else null,
                strokeWidth = if (isActive) "2" else null,
            )
        }
    }

    private fun FlowContent.renderAtomSvg(atom: MnNode.Atom, x: Double, topY: Double) {
        val isActive = atom.id == props.selection.atom?.id
        val isDragging = dragAtomId == atom.id
        val pos = if (isDragging) dragPreviewPos ?: dragStartPos else props.atomToPos(atom.value)

        if (pos == null) {
            renderUnknownSvg(atom, x, isActive, topY)
            return
        }

        val y = staffPosToY(pos, topY)
        val noteColor = when {
            isDragging -> "#2266cc"
            isActive -> "#3355aa"
            else -> "#222"
        }
        val strokeColor = if (isActive || isDragging) "#2266cc" else "#333"
        val strokeWidth = if (isActive || isDragging) "2" else "1.2"

        svgG(key = "atom-${atom.id}", style = "cursor:default") {
            attributes["data-atom-id"] = atom.id.toString()
            attributes["data-staff-pos"] = pos.toString()

            renderLedgerLines(pos, x, topY)

            val stemUp = pos < 6
            if (stemUp) {
                val stemX = x + NOTE_RADIUS_X - 0.5
                svgLine(stemX, y - NOTE_RADIUS_Y + 2, stemX, y - HALF_STEP * 3.5, stroke = noteColor)
            } else {
                val stemX = x - NOTE_RADIUS_X + 0.5
                svgLine(stemX, y + NOTE_RADIUS_Y - 2, stemX, y + HALF_STEP * 3.5, stroke = noteColor)
            }

            svgEllipse(x, y, NOTE_RADIUS_X, NOTE_RADIUS_Y, fill = noteColor, stroke = strokeColor, strokeWidth = strokeWidth)

            renderAccidental(atom.value, x, y)
        }
    }

    private fun FlowContent.renderUnknownSvg(atom: MnNode.Atom, x: Double, isActive: Boolean, topY: Double) {
        val pos = 0 // C4
        val y = staffPosToY(pos, topY)
        val color = if (isActive) "#2266cc" else "#999"
        svgG(key = "atom-${atom.id}", style = "cursor:default;user-select:none") {
            attributes["data-atom-id"] = atom.id.toString()
            attributes["data-staff-pos"] = pos.toString()
            renderLedgerLines(pos, x, topY)
            svgText(x, y + 3, text = "?", fill = color, fontSize = "11", textAnchor = "middle")
        }
    }

    private fun FlowContent.renderLedgerLines(pos: Int, x: Double, topY: Double) {
        val ledgerHalfW = NOTE_RADIUS_X + 2.5
        // Below staff: pos <= 0, every even position gets a ledger line
        var p = 0
        while (p >= pos) {
            svgLine(x - ledgerHalfW, staffPosToY(p, topY), x + ledgerHalfW, staffPosToY(p, topY), stroke = "#555")
            p -= 2
        }
        // Above staff: pos >= 12, every even position gets a ledger line
        p = 12
        while (p <= pos) {
            svgLine(x - ledgerHalfW, staffPosToY(p, topY), x + ledgerHalfW, staffPosToY(p, topY), stroke = "#555")
            p += 2
        }
    }

    private fun FlowContent.renderAccidental(value: String, x: Double, y: Double) {
        val acc = extractAccidental(value) ?: return
        val accText = when {
            acc.startsWith("##") || acc.startsWith("x") -> "\uD834\uDD2A" // 𝄪 double sharp
            acc.startsWith("#") -> "♯"
            acc.startsWith("bb") || acc.startsWith("ff") -> "\uD834\uDD2B" // 𝄫 double flat
            acc.startsWith("b") || acc.startsWith("f") -> "♭"
            else -> return
        }
        svgText(
            x - NOTE_RADIUS_X - 6.0, y + 3, text = accText,
            fontSize = "10", textAnchor = "middle", style = "user-select:none",
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
