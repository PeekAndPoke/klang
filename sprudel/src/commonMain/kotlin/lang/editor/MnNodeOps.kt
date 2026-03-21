package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.editor.MnNodeOps.groupInsertAt
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern

/**
 * Shared tree-walking operations on [MnPattern] / [MnNode].
 *
 * Most tree operations now live directly on [MnNode] (findById, replaceById, removeById, walk, etc.).
 * This object provides query helpers that the UI and editor still use.
 */
object MnNodeOps {

    // ── Collectors ────────────────────────────────────────────────────────────

    fun collectAtoms(pattern: MnPattern): List<MnNode.Atom> = buildList {
        pattern.walk { if (it is MnNode.Atom && it.sourceRange != null) add(it) }
    }

    fun collectRests(pattern: MnPattern): List<MnNode.Rest> = buildList {
        pattern.walk { if (it is MnNode.Rest && it.sourceRange != null) add(it) }
    }

    /** Collects atoms AND rests (both with source positions) in document order. */
    fun collectStaffItems(pattern: MnPattern): List<MnNode> = buildList {
        pattern.walk { n ->
            if ((n is MnNode.Atom || n is MnNode.Rest) && n.sourceRange != null) add(n)
        }
    }

    fun collectStacks(pattern: MnPattern): List<MnNode.Stack> = buildList {
        pattern.walk { if (it is MnNode.Stack) add(it) }
    }

    // ── Group normalization ─────────────────────────────────────────────────

    /**
     * Inserts [node] at [index] into a [MnNode.Group], wrapping any existing Stack children
     * in sub-Groups first so the Stack's comma-separated layers stay intact.
     *
     * Without wrapping, `Group([Stack(a,b)]).insertAt(0, x)` would produce `[x a,b]`
     * which the parser reads as a 2-layer stack with `x a` in the first layer.
     * Wrapping gives `[x [a,b]]` which preserves the chord.
     */
    fun groupInsertAt(group: MnNode.Group, index: Int, node: MnNode): MnNode.Group {
        val wrapped = if (group.items.any { it is MnNode.Stack }) {
            MnNode.Group(
                items = group.items.map { if (it is MnNode.Stack) MnNode.Group(items = listOf(it)) else it },
                mods = group.mods,
            )
        } else {
            group
        }
        return wrapped.insertAt(index, node)
    }

    /**
     * Unwraps unnecessary single-child Group nesting created by [groupInsertAt].
     *
     * Collapses `Group([Group([Stack(...)])])` → `Group([Stack(...)])` when the inner
     * Group has no modifiers (i.e. it was only a structural wrapper).
     */
    fun normalizeGroups(pattern: MnPattern): MnPattern =
        MnPattern(items = pattern.items.map { normalizeNode(it) })

    private fun normalizeNode(node: MnNode): MnNode = when (node) {
        is MnNode.Group -> {
            val normalizedItems = node.items.map { normalizeNode(it) }
            if (normalizedItems.size == 1) {
                val only = normalizedItems[0]
                if (only is MnNode.Group && only.mods.isEmpty) {
                    // Unwrap: outer Group absorbs inner Group's items
                    MnNode.Group(items = only.items, mods = node.mods)
                } else {
                    MnNode.Group(items = normalizedItems, mods = node.mods)
                }
            } else {
                MnNode.Group(items = normalizedItems, mods = node.mods)
            }
        }

        is MnNode.Stack -> {
            val nonEmpty = node.layers
                .map { layer -> layer.map { normalizeNode(it) } }
                .filter { it.isNotEmpty() }
            when {
                // Single layer with a single item → collapse to that item
                nonEmpty.size == 1 && nonEmpty[0].size == 1 -> nonEmpty[0][0]
                // Single layer with multiple items → unwrap into parent as a sequence
                nonEmpty.size == 1 -> MnNode.Stack(layers = nonEmpty, mods = node.mods)
                // Multiple layers → keep as stack
                nonEmpty.isNotEmpty() -> MnNode.Stack(layers = nonEmpty, mods = node.mods)
                // All layers empty → should not happen, but keep node
                else -> node
            }
        }

        is MnNode.Alternation -> MnNode.Alternation(items = node.items.map { normalizeNode(it) }, mods = node.mods)
        is MnNode.Choice -> MnNode.Choice(options = node.options.map { normalizeNode(it) }, mods = node.mods)
        is MnNode.Repeat -> MnNode.Repeat(node = normalizeNode(node.node), count = node.count, mods = node.mods)
        is MnPattern -> MnPattern(items = node.items.map { normalizeNode(it) })
        else -> node
    }

    // ── Finders ───────────────────────────────────────────────────────────────

    fun findAtomById(pattern: MnPattern, id: Int): MnNode.Atom? =
        pattern.findById(id) as? MnNode.Atom

    /** Finds the [MnNode.Repeat] that directly wraps the node with [childId], or null. */
    fun findParentRepeat(pattern: MnPattern, childId: Int): MnNode.Repeat? =
        pattern.findFirst { node ->
            (node as? MnNode.Repeat)?.takeIf { it.node.id == childId }
        }

    fun findAtomAtOffset(pattern: MnPattern, text: String, offset: Int): MnNode.Atom? {
        // Pass 1: exact hit inside sourceRange
        pattern.findFirst { n ->
            (n as? MnNode.Atom)?.takeIf { a -> a.sourceRange?.let { offset in it } == true }
        }?.let { return it }
        // Pass 2: modifier tail — cursor is past the value token with no separator in between
        val nearest = collectAtoms(pattern)
            .filter { it.sourceRange != null && it.sourceRange.last < offset }
            .maxByOrNull { it.sourceRange!!.last }
            ?: return null
        val atomEnd = nearest.sourceRange!!.last + 1
        val between = text.substring(
            atomEnd.coerceAtMost(text.length),
            offset.coerceAtMost(text.length),
        )

        return if (between.none { it.isWhitespace() || it in "[]<>," }) nearest else null
    }
}
