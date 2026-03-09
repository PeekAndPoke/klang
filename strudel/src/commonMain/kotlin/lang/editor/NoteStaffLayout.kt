package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.buildInsertTargets
import io.peekandpoke.klang.strudel.lang.editor.NoteStaffLayout.buildLayoutItems
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern

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

    /** Returns the flat list of layout items for [pattern]. */
    fun buildLayoutItems(pattern: MnPattern): List<LayoutItem> = buildList {
        pattern.items.forEachIndexed { idx, node ->
            buildLayoutItems(node, parentId = pattern.id, indexInParent = idx, result = this)
        }
    }

    private fun buildLayoutItems(node: MnNode, parentId: Int, indexInParent: Int, result: MutableList<LayoutItem>) {
        when (node) {
            is MnNode.Atom -> result.add(LayoutItem.Note(node, parentId, indexInParent))
            is MnNode.Rest -> if (node.sourceRange != null) result.add(LayoutItem.Note(node, parentId, indexInParent))
            is MnNode.Group -> {
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
                        if ((n is MnNode.Atom) || (n is MnNode.Rest && n.sourceRange != null)) add(n)
                    }
                }
                if (stackNodes.isNotEmpty()) result.add(LayoutItem.Stack(stackNodes, parentId, indexInParent))
            }

            is MnNode.Choice -> node.options.forEachIndexed { idx, child ->
                buildLayoutItems(child, parentId = node.id, indexInParent = idx, result = result)
            }

            is MnNode.Repeat -> buildLayoutItems(node.node, parentId = node.id, indexInParent = 0, result = result)
            is MnNode.Linebreak -> {}
            is MnPattern -> node.items.forEachIndexed { idx, child ->
                buildLayoutItems(child, parentId = node.id, indexInParent = idx, result = result)
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
                    // Center overlay: stack onto this node
                    add(SlotTarget(InsertTarget.StackOnto(item.items.first().id), idx, isPush = false))
                    // Right push: insert after this stack in its parent
                    add(SlotTarget(InsertTarget.Sequential(item.parentId, item.indexInParent + 1), idx + 1, isPush = true))
                }

                is LayoutItem.BracketMark -> {
                    if (item.isOpen) {
                        // Push slot at start of container: insert at index 0 inside the container
                        add(SlotTarget(InsertTarget.Sequential(item.containerId, 0), idx, isPush = true))
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
