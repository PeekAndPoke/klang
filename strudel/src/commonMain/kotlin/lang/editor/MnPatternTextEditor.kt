package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern

/**
 * Pure (platform-independent) editing logic for a mini-notation text pattern.
 *
 * Each mutating operation returns a **new** [MnPatternTextEditor] with the updated [text]
 * so callers (including tests) can chain operations and inspect intermediate states.
 *
 * The [posToValue] function maps a staff position integer to an atom value string
 * (e.g. 0 → "c4", 2 → "d4", …). Callers that don't use the staff can pass a no-op.
 */
class MnPatternTextEditor(
    val text: String,
    val posToValue: (Int) -> String = { "pos$it" },
) {

    // ── Derived ───────────────────────────────────────────────────────────────

    val pattern: MnPattern? by lazy {
        if (text.isBlank()) null else parseMiniNotationMnPattern(text)
    }

    // ── Node queries (delegate to MnNodeOps) ─────────────────────────────────

    fun atoms(): List<MnNode.Atom> = pattern?.let { MnNodeOps.collectAtoms(it) } ?: emptyList()

    fun rests(): List<MnNode.Rest> = pattern?.let { MnNodeOps.collectRests(it) } ?: emptyList()

    fun atomByValue(value: String): MnNode.Atom? = atoms().firstOrNull { it.value == value }

    fun atomAt(index: Int): MnNode.Atom? = atoms().getOrNull(index)

    fun stackNodes(): List<MnNode.Stack> = pattern?.let { MnNodeOps.collectStacks(it) } ?: emptyList()

    // ── Operations — each returns a new editor with the updated text ───────────

    /**
     * Removes [node] (Atom or Rest) from the pattern via text-based removal for clean whitespace.
     */
    fun removeNode(node: MnNode): MnPatternTextEditor {
        val range = node.sourceRange ?: return this
        if (node !is MnNode.Atom && node !is MnNode.Rest) return this
        val before = text.substring(0, range.first)
        val after = text.substring(range.last + 1)
        val newText = (before + after)
            .replace(Regex(" {2,}"), " ")
            .replace(Regex("([\\[<,]) "), "$1")
            .replace(Regex(" ([\\]>,])"), "$1")
            .trim()
        return copy(newText)
    }

    /**
     * Replaces [old] with [new] in the pattern tree and re-renders the whole string.
     */
    fun updateNode(old: MnNode, new: MnNode): MnPatternTextEditor {
        val p = pattern ?: return this
        return copy(MnRenderer.render(MnNodeOps.replaceNode(p, old, new)))
    }

    /**
     * Inserts a new atom between [leftNode] and [rightNode] using AST manipulation.
     *
     * Walks the parse tree to find the parent sequence containing the insertion point,
     * splices the new atom into that list, and re-renders to text via [MnRenderer].
     *
     * [skipOpeningBrackets] > 0 with null [leftNode]: inserts inside N levels of nesting.
     * [exitBrackets] > 0 with non-null [leftNode]: inserts after exiting N container levels.
     */
    fun insertBetween(
        leftNode: MnNode?,
        rightNode: MnNode?,
        staffPos: Int,
        skipOpeningBrackets: Int = 0,
        exitBrackets: Int = 0,
    ): MnPatternTextEditor {
        val p = pattern ?: return copy(posToValue(staffPos))
        val newAtom = MnNode.Atom(value = posToValue(staffPos))

        // Exit-bracket insertion: insert after leftNode but ascend N container levels first.
        if (exitBrackets > 0 && leftNode != null) {
            val result = MnNodeOps.insertAfterExitingBrackets(p, leftNode, newAtom, exitBrackets)
            return copy(MnRenderer.render(result))
        }

        // Skip-opening-brackets: insert inside nested brackets before rightNode.
        if (leftNode == null && rightNode != null && skipOpeningBrackets > 0) {
            val result = insertAtDepth(p.items, rightNode, newAtom, skipOpeningBrackets, 0)
            if (result != null) return copy(MnRenderer.render(MnPattern(result)))
        }

        val result = MnNodeOps.insertBetweenAst(p, leftNode, rightNode, newAtom)
        return copy(MnRenderer.render(result))
    }

    /**
     * Inserts a new atom at the same position as [existingNode]:
     * - [MnNode.Atom]  → wraps it in a stack: `c4` → `[c4,e4]`
     * - [MnNode.Stack] → appends to the existing stack: `[c4,e4]` → `[c4,e4,g4]`
     * - anything else  → falls back to [insertBetween] after [existingNode].
     */
    fun insertAt(existingNode: MnNode, staffPos: Int): MnPatternTextEditor {
        val newValue = posToValue(staffPos)
        return when (existingNode) {
            is MnNode.Atom -> {
                val range = existingNode.sourceRange ?: return copy("$text $newValue")
                val atomText = text.substring(range.first, range.last + 1)
                copy(text.substring(0, range.first) + "[$atomText,$newValue]" + text.substring(range.last + 1))
            }

            is MnNode.Stack -> {
                val lastEnd = existingNode.layers.flatten()
                    .mapNotNull { it.sourceRange?.last }
                    .maxOrNull() ?: return copy("$text $newValue")
                val closeIdx = text.indexOf(']', lastEnd)
                if (closeIdx < 0) return copy("$text $newValue")
                copy(text.substring(0, closeIdx) + ",$newValue" + text.substring(closeIdx))
            }

            else -> insertBetween(existingNode, null, staffPos)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun copy(newText: String) = MnPatternTextEditor(newText, posToValue)

    /**
     * Walks [skip] levels of Group/Alternation nesting, then inserts [newAtom] before [rightNode].
     */
    private fun insertAtDepth(
        items: List<MnNode>,
        rightNode: MnNode,
        newAtom: MnNode,
        targetDepth: Int,
        currentDepth: Int,
    ): List<MnNode>? {
        for ((i, item) in items.withIndex()) {
            val childItems = when (item) {
                is MnNode.Group -> item.items
                is MnNode.Alternation -> item.items
                else -> null
            }

            if (childItems != null) {
                val nextDepth = currentDepth + 1
                if (nextDepth == targetDepth) {
                    // Insert inside this container, before rightNode
                    val insertIdx = childItems.indexOfFirst { it.matchesOrContains(rightNode) }
                    if (insertIdx >= 0) {
                        val newChildren = childItems.toMutableList().apply { add(insertIdx, newAtom) }
                        val rebuilt = when (item) {
                            is MnNode.Group -> item.copy(items = newChildren)
                            is MnNode.Alternation -> item.copy(items = newChildren)
                            else -> error("unreachable")
                        }
                        return items.toMutableList().apply { set(i, rebuilt) }
                    }
                }
                // Recurse deeper
                val deeper = insertAtDepth(childItems, rightNode, newAtom, targetDepth, nextDepth)
                if (deeper != null) {
                    val rebuilt = when (item) {
                        is MnNode.Group -> item.copy(items = deeper)
                        is MnNode.Alternation -> item.copy(items = deeper)
                        else -> error("unreachable")
                    }
                    return items.toMutableList().apply { set(i, rebuilt) }
                }
            }

            // Recurse into Stack layers
            if (item is MnNode.Stack) {
                for ((li, layer) in item.layers.withIndex()) {
                    val deeper = insertAtDepth(layer, rightNode, newAtom, targetDepth, currentDepth)
                    if (deeper != null) {
                        val newLayers = item.layers.toMutableList().apply { set(li, deeper) }
                        return items.toMutableList().apply { set(i, item.copy(layers = newLayers)) }
                    }
                }
            }
        }
        return null
    }
}
