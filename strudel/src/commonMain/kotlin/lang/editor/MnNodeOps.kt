package io.peekandpoke.klang.strudel.lang.editor

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

    // ── Finders ───────────────────────────────────────────────────────────────

    fun findAtomById(pattern: MnPattern, id: Int): MnNode.Atom? =
        pattern.findById(id) as? MnNode.Atom

    fun findAtomAtOffset(pattern: MnPattern, text: String, offset: Int): MnNode.Atom? {
        // Pass 1: exact hit inside sourceRange
        pattern.findFirst { n ->
            (n as? MnNode.Atom)?.takeIf { a -> a.sourceRange?.let { offset in it } == true }
        }?.let { return it }
        // Pass 2: modifier tail — cursor is past the value token with no separator in between
        val nearest = collectAtoms(pattern)
            .filter { it.sourceRange != null && it.sourceRange!!.last < offset }
            .maxByOrNull { it.sourceRange!!.last }
            ?: return null
        val atomEnd = nearest.sourceRange!!.last + 1
        val between = text.substring(atomEnd.coerceAtMost(text.length), offset.coerceAtMost(text.length))
        return if (between.none { it.isWhitespace() || it in "[]<>," }) nearest else null
    }
}
