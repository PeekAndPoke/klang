package io.peekandpoke.klang.sprudel.lang.editor

import io.peekandpoke.klang.sprudel.lang.parser.MnNode

// ── Source range ─────────────────────────────────────────────────────────────

/** Returns the source range for Atom/Rest nodes, null for everything else. */
val MnNode.sourceRange: IntRange?
    get() = when (this) {
        is MnNode.Atom -> sourceRange
        is MnNode.Rest -> sourceRange
        else -> null
    }

// ── Node identity (legacy helpers — prefer MnNode.id for new code) ──────────

/** Identity comparison: matches nodes by [MnNode.id]. */
fun nodesMatch(a: MnNode, b: MnNode): Boolean = a.id == b.id

/** Checks whether [target] is nested somewhere inside this node. */
fun MnNode.containsNode(target: MnNode): Boolean =
    children().any { it.id == target.id || it.containsNode(target) }

/** Returns true if this node matches [target] directly or contains it as a descendant. */
fun MnNode.matchesOrContains(target: MnNode): Boolean =
    id == target.id || containsNode(target)
