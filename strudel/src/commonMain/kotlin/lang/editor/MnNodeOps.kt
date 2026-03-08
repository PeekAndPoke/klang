package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.editor.MnNodeOps.indexOfNodeDeep
import io.peekandpoke.klang.strudel.lang.editor.MnNodeOps.indexOfNodeDirect
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern

/**
 * Shared tree-walking operations on [MnPattern] / [MnNode].
 *
 * Both [MnPatternTextEditor] (commonMain) and the UI layer (jsMain) delegate here
 * instead of duplicating recursive traversals.
 */
object MnNodeOps {

    // ── Collectors ────────────────────────────────────────────────────────────

    fun collectAtoms(pattern: MnPattern): List<MnNode.Atom> = buildList {
        pattern.items.forEach { it.walk { n -> if (n is MnNode.Atom && n.sourceRange != null) add(n) } }
    }

    fun collectRests(pattern: MnPattern): List<MnNode.Rest> = buildList {
        pattern.items.forEach { it.walk { n -> if (n is MnNode.Rest && n.sourceRange != null) add(n) } }
    }

    /** Collects atoms AND rests (both with source positions) in document order. */
    fun collectStaffItems(pattern: MnPattern): List<MnNode> = buildList {
        pattern.items.forEach {
            it.walk { n ->
                if ((n is MnNode.Atom || n is MnNode.Rest) && n.sourceRange != null) add(n)
            }
        }
    }

    fun collectStacks(pattern: MnPattern): List<MnNode.Stack> = buildList {
        pattern.items.forEach { it.walk { n -> if (n is MnNode.Stack) add(n) } }
    }

    // ── Finders ───────────────────────────────────────────────────────────────

    fun findAtomById(pattern: MnPattern, id: Int): MnNode.Atom? =
        if (id < 0) null
        else pattern.items.firstNotNullOfOrNull { it.findFirst { n -> (n as? MnNode.Atom)?.takeIf { a -> a.id == id } } }

    fun findAtomAtOffset(pattern: MnPattern, text: String, offset: Int): MnNode.Atom? {
        // Pass 1: exact hit inside sourceRange
        pattern.items.firstNotNullOfOrNull {
            it.findFirst { n -> (n as? MnNode.Atom)?.takeIf { a -> a.sourceRange?.let { r -> offset in r } == true } }
        }?.let { return it }
        // Pass 2: modifier tail — cursor is past the value token with no separator in between
        val nearest = collectAtoms(pattern)
            .filter { it.sourceRange != null && it.sourceRange.last < offset }
            .maxByOrNull { it.sourceRange!!.last }
            ?: return null
        val atomEnd = nearest.sourceRange!!.last + 1
        val between = text.substring(atomEnd.coerceAtMost(text.length), offset.coerceAtMost(text.length))
        return if (between.none { it.isWhitespace() || it in "[]<>," }) nearest else null
    }

    // ── Tree replacement ──────────────────────────────────────────────────────

    fun replaceNode(pattern: MnPattern, old: MnNode, new: MnNode): MnPattern =
        MnPattern(pattern.items.map { replaceInNode(it, old, new) })

    // ── AST insertion ─────────────────────────────────────────────────────────

    /**
     * Inserts [newNode] into the AST between [leftNode] and [rightNode].
     *
     * Walks the tree to find the parent sequence (Group.items, Alternation.items, Stack layer,
     * or the top-level MnPattern.items) that contains the insertion point, then splices
     * [newNode] into that list.
     *
     * When [leftNode] is null, inserts before [rightNode].
     * When [rightNode] is null, inserts after [leftNode].
     * When both are null, appends to the pattern.
     */
    fun insertBetweenAst(
        pattern: MnPattern,
        leftNode: MnNode?,
        rightNode: MnNode?,
        newNode: MnNode,
    ): MnPattern {
        if (leftNode == null && rightNode == null) {
            return MnPattern(pattern.items + newNode)
        }
        val result = insertInList(pattern.items, leftNode, rightNode, newNode)
        if (result != null) return MnPattern(result)
        return MnPattern(pattern.items.map { insertInNode(it, leftNode, rightNode, newNode) })
    }

    // ── Exit-bracket insertion ────────────────────────────────────────────────

    /**
     * Inserts [newNode] after [leftNode] but exits [exitBrackets] container levels first.
     *
     * exitBrackets=1 means: find leftNode at its deepest position, go up one container
     * level, and insert after the child that contains leftNode.
     * exitBrackets=2 goes up two levels, etc.
     */
    fun insertAfterExitingBrackets(
        pattern: MnPattern,
        leftNode: MnNode,
        newNode: MnNode,
        exitBrackets: Int,
    ): MnPattern {
        val result = exitInsertInItems(pattern.items, leftNode, newNode, exitBrackets)
        return when (result) {
            is ExitListResult.Done -> MnPattern(result.items)
            else -> pattern
        }
    }

    // ── Private: tree replacement ─────────────────────────────────────────────

    private fun replaceInNode(node: MnNode, old: MnNode, new: MnNode): MnNode = when (node) {
        is MnNode.Atom -> if (old is MnNode.Atom && node.id == old.id) new else node
        is MnNode.Rest -> if (old is MnNode.Rest && old.sourceRange != null && node.sourceRange == old.sourceRange) new else node
        is MnNode.Group -> node.copy(items = node.items.map { replaceInNode(it, old, new) })
        is MnNode.Alternation -> node.copy(items = node.items.map { replaceInNode(it, old, new) })
        is MnNode.Stack -> node.copy(layers = node.layers.map { l -> l.map { replaceInNode(it, old, new) } })
        is MnNode.Choice -> node.copy(options = node.options.map { replaceInNode(it, old, new) })
        is MnNode.Repeat -> node.copy(node = replaceInNode(node.node, old, new))
        is MnNode.Linebreak -> node
    }

    // ── Private: AST insertion ────────────────────────────────────────────────

    /**
     * Checks if [leftNode]/[rightNode] are siblings in [items] and splices [newNode] between them.
     * Returns the new list, or null if the insertion point wasn't found at this level.
     *
     * When both nodes are present, uses [indexOfNodeDeep] (matchesOrContains) to find the
     * shallowest common ancestor — needed for cross-group inserts.
     *
     * When `rightNode == null`, uses [indexOfNodeDirect] (exact match only) so the search
     * doesn't match a containing group at the wrong level.
     */
    private fun insertInList(
        items: List<MnNode>,
        leftNode: MnNode?,
        rightNode: MnNode?,
        newNode: MnNode,
    ): List<MnNode>? {
        when {
            leftNode != null && rightNode != null -> {
                val leftIdx = items.indexOfNodeDeep(leftNode)
                val rightIdx = items.indexOfNodeDeep(rightNode)
                if (leftIdx >= 0 && rightIdx >= 0 && rightIdx > leftIdx) {
                    return items.toMutableList().apply { add(rightIdx, newNode) }
                }
                for ((i, item) in items.withIndex()) {
                    if (item is MnNode.Stack) {
                        val newStack = insertInStack(item, leftNode, rightNode, newNode)
                        if (newStack != null) return items.toMutableList().apply { set(i, newStack) }
                    }
                }
            }

            leftNode == null && rightNode != null -> {
                val rightIdx = items.indexOfNodeDeep(rightNode)
                if (rightIdx >= 0) {
                    return items.toMutableList().apply { add(rightIdx, newNode) }
                }
            }

            leftNode != null && rightNode == null -> {
                val leftIdx = items.indexOfNodeDirect(leftNode)
                if (leftIdx >= 0) {
                    return items.toMutableList().apply { add(leftIdx + 1, newNode) }
                }
            }
        }
        return null
    }

    private fun insertInStack(
        stack: MnNode.Stack,
        leftNode: MnNode?,
        rightNode: MnNode?,
        newNode: MnNode,
    ): MnNode.Stack? {
        for ((layerIdx, layer) in stack.layers.withIndex()) {
            val result = insertInList(layer, leftNode, rightNode, newNode)
            if (result != null) {
                return stack.copy(layers = stack.layers.toMutableList().apply { set(layerIdx, result) })
            }
        }
        return null
    }

    /**
     * Recursively walks the tree looking for a parent that contains the insertion point.
     *
     * When [rightNode] is null → depth-first (finds deepest match).
     * When [rightNode] is non-null → breadth-first (finds shallowest common ancestor).
     */
    private fun insertInNode(
        node: MnNode,
        leftNode: MnNode?,
        rightNode: MnNode?,
        newNode: MnNode,
    ): MnNode {
        val depthFirst = rightNode == null
        return when (node) {
            is MnNode.Group -> insertInContainer(
                items = node.items, leftNode = leftNode, rightNode = rightNode, newNode = newNode, depthFirst = depthFirst,
                wrapItems = { node.copy(items = it) },
                recurse = { items -> items.map { insertInNode(it, leftNode, rightNode, newNode) } },
            )

            is MnNode.Alternation -> insertInContainer(
                items = node.items, leftNode = leftNode, rightNode = rightNode, newNode = newNode, depthFirst = depthFirst,
                wrapItems = { node.copy(items = it) },
                recurse = { items -> items.map { insertInNode(it, leftNode, rightNode, newNode) } },
            )

            is MnNode.Stack -> {
                if (depthFirst) {
                    val recursed = node.layers.map { layer ->
                        layer.map { insertInNode(it, leftNode, rightNode, newNode) }
                    }
                    if (recursed != node.layers) return node.copy(layers = recursed)
                    insertInStack(node, leftNode, rightNode, newNode) ?: node
                } else {
                    val result = insertInStack(node, leftNode, rightNode, newNode)
                    result ?: node.copy(layers = node.layers.map { layer ->
                        layer.map { insertInNode(it, leftNode, rightNode, newNode) }
                    })
                }
            }

            is MnNode.Choice -> insertInContainer(
                items = node.options, leftNode = leftNode, rightNode = rightNode, newNode = newNode, depthFirst = depthFirst,
                wrapItems = { node.copy(options = it) },
                recurse = { items -> items.map { insertInNode(it, leftNode, rightNode, newNode) } },
            )

            is MnNode.Repeat -> node.copy(node = insertInNode(node.node, leftNode, rightNode, newNode))
            is MnNode.Atom, is MnNode.Rest, is MnNode.Linebreak -> node
        }
    }

    private fun insertInContainer(
        items: List<MnNode>,
        leftNode: MnNode?,
        rightNode: MnNode?,
        newNode: MnNode,
        depthFirst: Boolean,
        wrapItems: (List<MnNode>) -> MnNode,
        recurse: (List<MnNode>) -> List<MnNode>,
    ): MnNode {
        if (depthFirst) {
            val recursed = recurse(items)
            if (recursed != items) return wrapItems(recursed)
            val result = insertInList(items, leftNode, rightNode, newNode)
            return if (result != null) wrapItems(result) else wrapItems(items)
        } else {
            val result = insertInList(items, leftNode, rightNode, newNode)
            return if (result != null) wrapItems(result)
            else wrapItems(recurse(items))
        }
    }

    // ── Private: exit-bracket insertion ───────────────────────────────────────

    private sealed class ExitListResult {
        object NotFound : ExitListResult()
        data class Done(val items: List<MnNode>) : ExitListResult()
        data class NeedExit(val remaining: Int) : ExitListResult()
    }

    private sealed class ExitNodeResult {
        object NotFound : ExitNodeResult()
        data class Done(val node: MnNode) : ExitNodeResult()
        data class NeedExit(val remaining: Int) : ExitNodeResult()
    }

    private fun exitInsertInItems(
        items: List<MnNode>,
        leftNode: MnNode,
        newNode: MnNode,
        exitBrackets: Int,
    ): ExitListResult {
        for ((i, item) in items.withIndex()) {
            if (nodesMatch(item, leftNode)) {
                return if (exitBrackets <= 0) {
                    ExitListResult.Done(items.toMutableList().apply { add(i + 1, newNode) })
                } else {
                    ExitListResult.NeedExit(exitBrackets)
                }
            }

            val childResult = exitInsertInNode(item, leftNode, newNode, exitBrackets)
            when (childResult) {
                is ExitNodeResult.Done -> {
                    return ExitListResult.Done(items.toMutableList().apply { set(i, childResult.node) })
                }

                is ExitNodeResult.NeedExit -> {
                    return if (childResult.remaining <= 1) {
                        ExitListResult.Done(items.toMutableList().apply { add(i + 1, newNode) })
                    } else {
                        ExitListResult.NeedExit(childResult.remaining - 1)
                    }
                }

                is ExitNodeResult.NotFound -> { /* continue */
                }
            }
        }
        return ExitListResult.NotFound
    }

    private fun exitInsertInNode(
        node: MnNode,
        leftNode: MnNode,
        newNode: MnNode,
        exitBrackets: Int,
    ): ExitNodeResult {
        fun wrapListResult(r: ExitListResult, rebuild: (List<MnNode>) -> MnNode): ExitNodeResult = when (r) {
            is ExitListResult.Done -> ExitNodeResult.Done(rebuild(r.items))
            is ExitListResult.NeedExit -> ExitNodeResult.NeedExit(r.remaining)
            is ExitListResult.NotFound -> ExitNodeResult.NotFound
        }

        return when (node) {
            is MnNode.Group -> wrapListResult(exitInsertInItems(node.items, leftNode, newNode, exitBrackets)) { node.copy(items = it) }
            is MnNode.Alternation -> wrapListResult(
                exitInsertInItems(
                    node.items,
                    leftNode,
                    newNode,
                    exitBrackets
                )
            ) { node.copy(items = it) }

            is MnNode.Stack -> {
                var result: ExitNodeResult = ExitNodeResult.NotFound
                for ((li, layer) in node.layers.withIndex()) {
                    val r = exitInsertInItems(layer, leftNode, newNode, exitBrackets)
                    if (r !is ExitListResult.NotFound) {
                        result = wrapListResult(r) { node.copy(layers = node.layers.toMutableList().apply { set(li, it) }) }
                        break
                    }
                }
                result
            }

            is MnNode.Choice -> wrapListResult(exitInsertInItems(node.options, leftNode, newNode, exitBrackets)) { node.copy(options = it) }
            is MnNode.Repeat -> exitInsertInNode(node.node, leftNode, newNode, exitBrackets)
            is MnNode.Atom, is MnNode.Rest, is MnNode.Linebreak -> ExitNodeResult.NotFound
        }
    }

    // ── Private: node identity ────────────────────────────────────────────────

    private fun List<MnNode>.indexOfNodeDeep(target: MnNode): Int {
        for ((i, item) in this.withIndex()) {
            if (item.matchesOrContains(target)) return i
        }
        return -1
    }

    private fun List<MnNode>.indexOfNodeDirect(target: MnNode): Int {
        for ((i, item) in this.withIndex()) {
            if (nodesMatch(item, target)) return i
        }
        return -1
    }
}
