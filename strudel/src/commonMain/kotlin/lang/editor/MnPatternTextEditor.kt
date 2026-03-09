package io.peekandpoke.klang.strudel.lang.editor

import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern

/**
 * Pure (platform-independent) editing logic for a mini-notation pattern.
 *
 * Each mutating operation returns a **new** [MnPatternTextEditor] with the updated tree/text,
 * so callers can chain operations and inspect intermediate states.
 *
 * The core operation is [replaceNode]: find a node by id and swap it with a replacement.
 * Domain-specific mutations are done by callers using the node's own methods (e.g. Group.insertAt)
 * before passing the result to replaceNode.
 */
class MnPatternTextEditor(val text: String) {

    // ── Derived ───────────────────────────────────────────────────────────────

    val pattern: MnPattern? by lazy {
        if (text.isBlank()) null else parseMiniNotationMnPattern(text)
    }

    // ── Core operations ─────────────────────────────────────────────────────

    /** Replaces the node with [targetId] by [replacement], re-rendering the whole tree. */
    fun replaceNode(targetId: Int, replacement: MnNode): MnPatternTextEditor {
        val p = pattern ?: return this
        val newRoot = p.replaceById(targetId, replacement) as? MnPattern ?: return this
        return MnPatternTextEditor(MnRenderer.render(newRoot))
    }

    /** Removes the node with [targetId] from the tree, re-rendering the result. */
    fun removeNode(targetId: Int): MnPatternTextEditor {
        val p = pattern ?: return this
        val newRoot = p.removeById(targetId) as? MnPattern ?: return MnPatternTextEditor("")
        return MnPatternTextEditor(MnRenderer.render(newRoot))
    }
}
