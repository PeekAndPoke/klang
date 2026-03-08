package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.parser.MnNode

// ── Source range ─────────────────────────────────────────────────────────────

/** Returns the source range for Atom/Rest nodes, null for everything else. */
val MnNode.sourceRange: IntRange?
    get() = when (this) {
        is MnNode.Atom -> sourceRange
        is MnNode.Rest -> sourceRange
        else -> null
    }

// ── Tree structure ───────────────────────────────────────────────────────────

/** Returns the direct children of this node as a flat list. */
fun MnNode.children(): List<MnNode> = when (this) {
    is MnNode.Group -> items
    is MnNode.Alternation -> items
    is MnNode.Stack -> layers.flatten()
    is MnNode.Choice -> options
    is MnNode.Repeat -> listOf(node)
    is MnNode.Atom, is MnNode.Rest, is MnNode.Linebreak -> emptyList()
}

/** Depth-first traversal — calls [action] on this node, then recursively on all descendants. */
fun MnNode.walk(action: (MnNode) -> Unit) {
    action(this)
    children().forEach { it.walk(action) }
}

/** Depth-first search — returns the first non-null result of [match] applied to this node or any descendant. */
fun <T> MnNode.findFirst(match: (MnNode) -> T?): T? {
    match(this)?.let { return it }
    return children().firstNotNullOfOrNull { it.findFirst(match) }
}

// ── Node identity ────────────────────────────────────────────────────────────

/** Identity comparison for parsed nodes: matches Atoms by [MnNode.Atom.id], Rests by [MnNode.Rest.sourceRange]. */
fun nodesMatch(a: MnNode, b: MnNode): Boolean = when {
    a is MnNode.Atom && b is MnNode.Atom -> a.id == b.id && a.id >= 0
    a is MnNode.Rest && b is MnNode.Rest -> a.sourceRange != null && a.sourceRange == b.sourceRange
    else -> false
}

/** Checks whether [target] is nested somewhere inside this node (not a direct match). */
fun MnNode.containsNode(target: MnNode): Boolean =
    children().any { nodesMatch(it, target) || it.containsNode(target) }

/** Returns true if this node matches [target] directly or contains it as a descendant. */
fun MnNode.matchesOrContains(target: MnNode): Boolean =
    nodesMatch(this, target) || containsNode(target)
