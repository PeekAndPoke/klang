package io.peekandpoke.klang.sprudel.lang.editor

import io.peekandpoke.klang.sprudel.lang.editor.NoteStaffLayout.buildInsertTargets
import io.peekandpoke.klang.sprudel.lang.editor.NoteStaffLayout.buildLayoutItems
import io.peekandpoke.klang.sprudel.lang.parser.MnNode
import io.peekandpoke.klang.sprudel.lang.parser.MnPattern

/**
 * Platform-independent layout model for the note staff editor.
 *
 * Covers:
 * - [LayoutItem]: the flat list of columns the staff renders (Notes, Stacks, BracketMarks).
 * - [InsertTarget]: what action fires when the user double-clicks at a slot.
 * - [buildLayoutItems]: maps an [MnPattern] to a [LayoutItem] list.
 * - [buildInsertTargets]: maps a [LayoutItem] list to an ordered list of [SlotTarget]s.
 */
object NoteStaffLayout {

    // ── Types ──────────────────────────────────────────────────────────────────

    enum class BracketType { Group, Alternation }

    sealed class LayoutItem {
        /** A single atom or rest. */
        data class Note(
            val node: MnNode,
            val parentId: Int,
            val indexInParent: Int,
        ) : LayoutItem()

        /** A chord — simultaneous notes from MnNode.Stack. */
        data class Stack(
            val nodeId: Int,
            val items: List<MnNode>,
            val parentId: Int,
            val indexInParent: Int,
        ) : LayoutItem()

        /** Visual bracket column for Group or Alternation. */
        data class BracketMark(
            val type: BracketType,
            val isOpen: Boolean,
            val containerId: Int,
            /** The id of the container's parent (needed for close-bracket "exit" slots). */
            val containerParentId: Int,
            /** The index of the container in its parent's child list. */
            val containerIndexInParent: Int,
        ) : LayoutItem()
    }

    /** What happens when the user double-clicks at a snap slot. */
    sealed interface InsertTarget {
        /** Insert a new child into the container with [parentId] at [index]. */
        data class Sequential(val parentId: Int, val index: Int) : InsertTarget

        /** Overlay an existing column — add note to the stack at [nodeId]. */
        data class StackOnto(val nodeId: Int) : InsertTarget
    }

    /**
     * A logical snap slot (without pixel position).
     * [itemIdx] is the index into the layout items list that this slot belongs to.
     * [isPush] true = sequential insert (shifts items right), false = overlay (chord insert).
     */
    data class SlotTarget(val target: InsertTarget, val itemIdx: Int, val isPush: Boolean)

    // ── Layout building ────────────────────────────────────────────────────────

    /**
     * Returns the flat list of layout items for [pattern], filtered to [lineRange].
     *
     * Only leaf nodes (Atom, Rest) whose [sourceRange] falls within [lineRange] are included.
     * Container nodes (Group, Alternation) emit bracket marks only when they contribute
     * at least one visible leaf — but their original IDs are always preserved, so insert
     * targets reference the real tree.
     */
    fun buildLayoutItems(pattern: MnPattern, lineRange: IntRange): List<LayoutItem> = buildList {
        pattern.items.forEachIndexed { idx, node ->
            buildLayoutItems(node, parentId = pattern.id, indexInParent = idx, lineRange = lineRange, result = this)
        }
    }

    /** Returns true if [node] has at least one Atom/Rest leaf whose sourceRange intersects [lineRange]. */
    private fun hasVisibleLeaf(node: MnNode, lineRange: IntRange): Boolean = when (node) {
        is MnNode.Atom -> node.sourceRange?.first?.let { it in lineRange } == true
        is MnNode.Rest -> node.sourceRange?.first?.let { it in lineRange } == true
        is MnNode.Linebreak -> false
        else -> node.children().any { hasVisibleLeaf(it, lineRange) }
    }

    private fun buildLayoutItems(
        node: MnNode,
        parentId: Int,
        indexInParent: Int,
        lineRange: IntRange,
        result: MutableList<LayoutItem>,
    ) {
        fun MnNode.inRange() = sourceRange?.first?.let { it in lineRange } == true

        when (node) {
            is MnNode.Atom -> if (node.inRange()) result.add(LayoutItem.Note(node, parentId, indexInParent))
            is MnNode.Rest -> if (node.sourceRange != null && node.inRange()) result.add(LayoutItem.Note(node, parentId, indexInParent))

            is MnNode.Group -> {
                if (!hasVisibleLeaf(node, lineRange)) return
                result.add(
                    LayoutItem.BracketMark(
                        BracketType.Group,
                        isOpen = true,
                        containerId = node.id,
                        containerParentId = parentId,
                        containerIndexInParent = indexInParent
                    )
                )
                node.items.forEachIndexed { idx, child ->
                    buildLayoutItems(
                        child,
                        parentId = node.id,
                        indexInParent = idx,
                        lineRange = lineRange,
                        result = result
                    )
                }
                result.add(
                    LayoutItem.BracketMark(
                        BracketType.Group,
                        isOpen = false,
                        containerId = node.id,
                        containerParentId = parentId,
                        containerIndexInParent = indexInParent
                    )
                )
            }

            is MnNode.Alternation -> {
                if (!hasVisibleLeaf(node, lineRange)) return
                result.add(
                    LayoutItem.BracketMark(
                        BracketType.Alternation,
                        isOpen = true,
                        containerId = node.id,
                        containerParentId = parentId,
                        containerIndexInParent = indexInParent
                    )
                )
                node.items.forEachIndexed { idx, child ->
                    buildLayoutItems(
                        child,
                        parentId = node.id,
                        indexInParent = idx,
                        lineRange = lineRange,
                        result = result
                    )
                }
                result.add(
                    LayoutItem.BracketMark(
                        BracketType.Alternation,
                        isOpen = false,
                        containerId = node.id,
                        containerParentId = parentId,
                        containerIndexInParent = indexInParent
                    )
                )
            }

            is MnNode.Stack -> {
                val stackNodes = buildList<MnNode> {
                    node.walk { n ->
                        if ((n is MnNode.Atom || (n is MnNode.Rest && n.sourceRange != null)) && n.inRange()) add(n)
                    }
                }
                if (stackNodes.isNotEmpty()) result.add(LayoutItem.Stack(node.id, stackNodes, parentId, indexInParent))
            }

            is MnNode.Choice -> node.options.forEachIndexed { idx, child ->
                buildLayoutItems(child, parentId = node.id, indexInParent = idx, lineRange = lineRange, result = result)
            }

            is MnNode.Repeat -> buildLayoutItems(node.node, parentId = node.id, indexInParent = 0, lineRange = lineRange, result = result)
            is MnNode.Linebreak -> {}
            is MnPattern -> node.items.forEachIndexed { idx, child ->
                buildLayoutItems(child, parentId = node.id, indexInParent = idx, lineRange = lineRange, result = result)
            }
        }
    }

    // ── Slot target building ───────────────────────────────────────────────────

    /**
     * Returns the ordered list of [SlotTarget]s for [layoutItems].
     *
     * Each Note/Stack item produces:
     * - A **left-push** slot before it.
     * - A **center** overlay slot.
     * - A **right-push** slot at its right edge.
     *
     * Open brackets produce a push slot for inserting at the start of the container.
     * Close brackets produce a push slot for inserting after the container in its parent.
     */
    fun buildInsertTargets(layoutItems: List<LayoutItem>): List<SlotTarget> = buildList {
        for ((idx, item) in layoutItems.withIndex()) {
            when (item) {
                is LayoutItem.Note -> {
                    // Left push: insert before this item in its parent
                    add(SlotTarget(InsertTarget.Sequential(item.parentId, item.indexInParent), idx, isPush = true))
                    // Center overlay: stack onto this node
                    add(SlotTarget(InsertTarget.StackOnto(item.node.id), idx, isPush = false))
                    // Right push: insert after this item in its parent
                    add(SlotTarget(InsertTarget.Sequential(item.parentId, item.indexInParent + 1), idx + 1, isPush = true))
                }

                is LayoutItem.Stack -> {
                    // Left push: insert before this stack in its parent
                    add(SlotTarget(InsertTarget.Sequential(item.parentId, item.indexInParent), idx, isPush = true))
                    // Center overlay: add layer to this stack
                    add(SlotTarget(InsertTarget.StackOnto(item.nodeId), idx, isPush = false))
                    // Right push: insert after this stack in its parent
                    add(SlotTarget(InsertTarget.Sequential(item.parentId, item.indexInParent + 1), idx + 1, isPush = true))
                }

                is LayoutItem.BracketMark -> {
                    if (item.isOpen) {
                        // Push slot before container in parent (left edge of bracket)
                        add(SlotTarget(InsertTarget.Sequential(item.containerParentId, item.containerIndexInParent), idx, isPush = true))
                        // Push slot at start of container (right edge of bracket = left edge of next item)
                        add(SlotTarget(InsertTarget.Sequential(item.containerId, 0), idx + 1, isPush = true))
                    } else {
                        // Push slot after container: insert after the container in its parent
                        add(
                            SlotTarget(
                                InsertTarget.Sequential(item.containerParentId, item.containerIndexInParent + 1),
                                idx, isPush = true,
                            )
                        )
                    }
                }
            }
        }
    }
}
