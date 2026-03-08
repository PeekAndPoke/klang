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

    val pattern: MnPattern?
        get() = if (text.isBlank()) null else parseMiniNotationMnPattern(text)

    // ── Node queries ──────────────────────────────────────────────────────────

    fun atoms(): List<MnNode.Atom> = buildList {
        pattern?.items?.forEach { collectAtoms(it, this) }
    }

    fun rests(): List<MnNode.Rest> = buildList {
        pattern?.items?.forEach { collectRests(it, this) }
    }

    fun atomByValue(value: String): MnNode.Atom? = atoms().firstOrNull { it.value == value }

    fun atomAt(index: Int): MnNode.Atom? = atoms().getOrNull(index)

    fun stackNodes(): List<MnNode.Stack> = buildList {
        pattern?.items?.forEach { collectStacks(it, this) }
    }

    // ── Operations — each returns a new editor with the updated text ───────────

    /**
     * Removes [node] (Atom or Rest) from the text.
     * Collapses any resulting double-spaces and trims.
     */
    fun removeNode(node: MnNode): MnPatternTextEditor {
        val range = when (node) {
            is MnNode.Atom -> node.sourceRange
            is MnNode.Rest -> node.sourceRange
            else -> null
        } ?: return this
        val before = text.substring(0, range.first)
        val after = text.substring(range.last + 1)
        val newText = (before + after)
            .replace(Regex(" {2,}"), " ")          // collapse double spaces
            .replace(Regex("([\\[<,]) "), "$1")    // drop space after structural open chars
            .replace(Regex(" ([\\]>,])"), "$1")    // drop space before structural close chars
            .trim()
        return copy(newText)
    }

    /**
     * Replaces [old] with [new] in the pattern tree and re-renders the whole string.
     */
    fun updateNode(old: MnNode, new: MnNode): MnPatternTextEditor {
        val p = pattern ?: return this
        val newPattern = MnPattern(p.items.map { replaceNodeInNode(it, old, new) })
        return copy(MnRenderer.render(newPattern))
    }

    /**
     * Inserts a new atom with value [posToValue]\([staffPos]\) between [leftNode] and [rightNode].
     *
     * - If both nodes are given → scans forward from `leftNode.end+1` to find the gap:
     *   Phase 1 skips leftNode's modifier suffix chars (`*`, `/`, `?`, `@`, `(`, digits, etc.);
     *   Phase 2 skips closing `]`/`>` and spaces.  This places the insertion BEFORE any opening
     *   bracket of rightNode's enclosing group — e.g. `insertBetween(d, e)` in `<a [c d] [e f]>`
     *   inserts between `[c d]` and `[e f]`, not inside `[e f]`.
     * - If only [rightNode] is given → inserts right at `rightNode.sourceRangeStart()`.
     * - If only [leftNode] is given → inserts after leftNode's modifier suffix.
     * - Otherwise → appends to the end of the text.
     */
    fun insertBetween(leftNode: MnNode?, rightNode: MnNode?, staffPos: Int): MnPatternTextEditor {
        val newValue = posToValue(staffPos)
        val insertPos = when {
            rightNode != null -> {
                val rightStart = rightNode.sourceRangeStart() ?: return copy("$text $newValue")
                val searchFrom = leftNode?.sourceRangeEnd()?.let { it + 1 } ?: 0
                var pos = searchFrom
                // Phase 1 (only when leftNode present): skip leftNode's modifier suffix so we
                // don't land in the middle of e.g. `*2` when sourceRange covers only the bare value.
                if (leftNode != null) {
                    while (pos < rightStart && text[pos].isModifierChar()) pos++
                }
                // Phase 2: skip closing brackets and spaces to land before any opening bracket
                // that wraps rightNode (e.g. the [ in [e f] when rightNode = e).
                while (pos < rightStart && (text[pos] == ']' || text[pos] == '>' || text[pos] == ' ')) pos++
                pos
            }

            leftNode != null -> {
                // rightNode is null: insert after leftNode including its modifier suffix.
                val end = leftNode.sourceRangeEnd() ?: return copy("$text $newValue")
                var pos = end + 1
                while (pos < text.length && text[pos].isModifierChar()) pos++
                pos
            }

            else -> null
        }

        return if (insertPos == null) {
            copy(if (text.isBlank()) newValue else "$text $newValue")
        } else {
            val before = text.substring(0, insertPos)
            val after = text.substring(insertPos)
            // Space before newValue unless the preceding char already separates (space, open-bracket, comma).
            val prefix = if (before.isNotEmpty() && !before.last().noSpaceNeededAfter()) " " else ""
            // Space after newValue unless the following char is self-separating (space, close-bracket, comma).
            val suffix = if (after.isNotEmpty() && !after.first().noSpaceNeededBefore()) " " else ""
            copy(before + prefix + newValue + suffix + after)
        }
    }

    // Modifier suffix char — belongs to a node's modifier (*, /, ?, @, (, ), digits, etc.)
    // but is NOT a structural separator (space, bracket, comma).
    private fun Char.isModifierChar() = this != ' ' && this !in "[]<>,"

    // A char after which no extra space is needed (already separated by the char itself).
    private fun Char.noSpaceNeededAfter() = this == ' ' || this in "[<,"

    // A char before which no extra space is needed.
    private fun Char.noSpaceNeededBefore() = this == ' ' || this in "]>,"

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
                    .mapNotNull {
                        when (it) {
                            is MnNode.Atom -> it.sourceRange?.last
                            is MnNode.Rest -> it.sourceRange?.last
                            else -> null
                        }
                    }.maxOrNull() ?: return copy("$text $newValue")
                val closeIdx = text.indexOf(']', lastEnd)
                if (closeIdx < 0) return copy("$text $newValue")
                copy(text.substring(0, closeIdx) + ",$newValue" + text.substring(closeIdx))
            }

            else -> insertBetween(existingNode, null, staffPos)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun copy(newText: String) = MnPatternTextEditor(newText, posToValue)

    private fun MnNode.sourceRangeEnd(): Int? = when (this) {
        is MnNode.Atom -> sourceRange?.last
        is MnNode.Rest -> sourceRange?.last
        else -> null
    }

    private fun MnNode.sourceRangeStart(): Int? = when (this) {
        is MnNode.Atom -> sourceRange?.first
        is MnNode.Rest -> sourceRange?.first
        else -> null
    }

    private fun collectAtoms(node: MnNode, out: MutableList<MnNode.Atom>) {
        when (node) {
            is MnNode.Atom -> if (node.sourceRange != null) out.add(node)
            is MnNode.Group -> node.items.forEach { collectAtoms(it, out) }
            is MnNode.Alternation -> node.items.forEach { collectAtoms(it, out) }
            is MnNode.Stack -> node.layers.flatten().forEach { collectAtoms(it, out) }
            is MnNode.Choice -> node.options.forEach { collectAtoms(it, out) }
            is MnNode.Repeat -> collectAtoms(node.node, out)
            is MnNode.Rest, is MnNode.Linebreak -> {}
        }
    }

    private fun collectRests(node: MnNode, out: MutableList<MnNode.Rest>) {
        when (node) {
            is MnNode.Rest -> if (node.sourceRange != null) out.add(node)
            is MnNode.Group -> node.items.forEach { collectRests(it, out) }
            is MnNode.Alternation -> node.items.forEach { collectRests(it, out) }
            is MnNode.Stack -> node.layers.flatten().forEach { collectRests(it, out) }
            is MnNode.Choice -> node.options.forEach { collectRests(it, out) }
            is MnNode.Repeat -> collectRests(node.node, out)
            is MnNode.Atom, is MnNode.Linebreak -> {}
        }
    }

    private fun collectStacks(node: MnNode, out: MutableList<MnNode.Stack>) {
        when (node) {
            is MnNode.Stack -> out.add(node)
            is MnNode.Group -> node.items.forEach { collectStacks(it, out) }
            is MnNode.Alternation -> node.items.forEach { collectStacks(it, out) }
            is MnNode.Choice -> node.options.forEach { collectStacks(it, out) }
            is MnNode.Repeat -> collectStacks(node.node, out)
            is MnNode.Atom, is MnNode.Rest, is MnNode.Linebreak -> {}
        }
    }

    private fun replaceNodeInNode(node: MnNode, old: MnNode, new: MnNode): MnNode = when (node) {
        is MnNode.Atom -> if (old is MnNode.Atom && node.id == old.id) new else node
        is MnNode.Rest -> if (old is MnNode.Rest && old.sourceRange != null && node.sourceRange == old.sourceRange) new else node
        is MnNode.Group -> node.copy(items = node.items.map { replaceNodeInNode(it, old, new) })
        is MnNode.Alternation -> node.copy(items = node.items.map { replaceNodeInNode(it, old, new) })
        is MnNode.Stack -> node.copy(layers = node.layers.map { l -> l.map { replaceNodeInNode(it, old, new) } })
        is MnNode.Choice -> node.copy(options = node.options.map { replaceNodeInNode(it, old, new) })
        is MnNode.Repeat -> node.copy(node = replaceNodeInNode(node.node, old, new))
        is MnNode.Linebreak -> node
    }
}
