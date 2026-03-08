package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.buildInsertTargets
import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.buildLayoutItems
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern

/**
 * Platform-independent layout model for the note staff editor.
 *
 * Extracts the rendering-independent logic from NoteStaffEditor (jsMain) so it can be tested
 * in commonTest / jvmTest without a browser.
 *
 * Covers:
 * - [LayoutItem]: the flat list of columns that the staff renders (Notes, Stacks, BracketMarks).
 * - [InsertTarget]: what action fires when the user double-clicks at a slot.
 * - [buildLayoutItems]: maps an [MnPattern] to a [LayoutItem] list.
 * - [buildInsertTargets]: maps a [LayoutItem] list to an ordered list of [SlotTarget]s
 *   (same logic as the boundary-building loop in NoteStaffEditor, without pixel coordinates).
 */
object NoteStaffLayout {

    // ── Types ──────────────────────────────────────────────────────────────────

    enum class BracketType { Group, Alternation }

    sealed class LayoutItem {
        data class Note(val node: MnNode) : LayoutItem()
        data class Stack(val items: List<MnNode>) : LayoutItem()
        data class BracketMark(val type: BracketType, val isOpen: Boolean) : LayoutItem()
    }

    /** What happens when the user double-clicks at a snap slot. */
    sealed interface InsertTarget {
        /**
         * Insert a new note between [leftNode] and [rightNode].
         * [skipOpeningBrackets] > 0 when [leftNode] is null and the slot is INSIDE one or more
         * opening brackets — see [MnPatternTextEditor.insertBetween].
         */
        data class Between(
            val leftNode: MnNode?,
            val rightNode: MnNode?,
            val skipOpeningBrackets: Int = 0,
            val exitBrackets: Int = 0,
        ) : InsertTarget

        /** Overlay an existing column — add note to the stack at [node]. */
        data class At(val node: MnNode) : InsertTarget
    }

    /**
     * A logical snap slot (without pixel position).
     * [itemIdx] is the index into the layout items list that this slot belongs to.
     * [isPush] true = sequential insert (shifts items right), false = overlay (chord insert).
     */
    data class SlotTarget(val target: InsertTarget, val itemIdx: Int, val isPush: Boolean)

    // ── Layout building ────────────────────────────────────────────────────────

    /** Returns the flat list of layout items for [pattern]. */
    fun buildLayoutItems(pattern: MnPattern): List<LayoutItem> = buildList {
        pattern.items.forEach { buildLayoutItems(it, this) }
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
                val stackNodes = buildList<MnNode> {
                    node.walk { n ->
                        if ((n is MnNode.Atom) || (n is MnNode.Rest && n.sourceRange != null)) add(n)
                    }
                }
                if (stackNodes.isNotEmpty()) result.add(LayoutItem.Stack(stackNodes))
            }

            is MnNode.Choice -> node.options.forEach { buildLayoutItems(it, result) }
            is MnNode.Repeat -> buildLayoutItems(node.node, result)
            is MnNode.Linebreak -> {}
        }
    }

    // ── Slot target building ───────────────────────────────────────────────────

    /**
     * Returns the ordered list of [SlotTarget]s for [layoutItems].
     *
     * Each item produces:
     * - A **left-push** slot before it (skipped for closing brackets).
     * - A **center** overlay slot (Note/Stack only).
     * - A **right-push** slot at its right edge (Note/Stack only).
     * A final push slot is added after the last item.
     *
     * This mirrors the boundary-building loop in NoteStaffEditor (jsMain) but without
     * pixel coordinates, making it testable from commonTest/jvmTest.
     */
    fun buildInsertTargets(layoutItems: List<LayoutItem>): List<SlotTarget> {
        // Precompute first renderable node at-or-after each layout index.
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

        return buildList {
            var leftNode: MnNode? = null
            var openBracketsSinceLastNode = 0
            var closeBracketsSinceLastNode = 0

            for ((idx, item) in layoutItems.withIndex()) {
                val isCloseBracket = item is LayoutItem.BracketMark && !item.isOpen

                if (!isCloseBracket) {
                    val rightForSlot = firstNodeAfter[idx]
                    val skipForSlot = if (leftNode == null) openBracketsSinceLastNode else 0
                    add(SlotTarget(InsertTarget.Between(leftNode, rightForSlot, skipForSlot), idx, isPush = true))
                }

                when (item) {
                    is LayoutItem.Note -> {
                        add(SlotTarget(InsertTarget.At(item.node), idx, isPush = false))
                        add(SlotTarget(InsertTarget.Between(item.node, firstNodeAfter[idx + 1], 0), idx + 1, isPush = true))
                        leftNode = item.node
                        openBracketsSinceLastNode = 0
                        closeBracketsSinceLastNode = 0
                    }

                    is LayoutItem.Stack -> {
                        add(SlotTarget(InsertTarget.At(item.items.first()), idx, isPush = false))
                        add(SlotTarget(InsertTarget.Between(item.items.lastOrNull(), firstNodeAfter[idx + 1], 0), idx + 1, isPush = true))
                        leftNode = item.items.lastOrNull()
                        openBracketsSinceLastNode = 0
                        closeBracketsSinceLastNode = 0
                    }

                    is LayoutItem.BracketMark -> {
                        if (item.isOpen) {
                            openBracketsSinceLastNode++
                            closeBracketsSinceLastNode = 0
                        } else {
                            closeBracketsSinceLastNode++
                            // Generate a close-bracket push slot: insert after leftNode,
                            // exiting N bracket levels from the atom's deepest position.
                            if (leftNode != null) {
                                add(
                                    SlotTarget(
                                        InsertTarget.Between(leftNode, null, 0, exitBrackets = closeBracketsSinceLastNode),
                                        idx, isPush = true,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            // Final trailing push slot.
            add(SlotTarget(InsertTarget.Between(leftNode, null, 0), layoutItems.size, isPush = true))
        }
    }
}
