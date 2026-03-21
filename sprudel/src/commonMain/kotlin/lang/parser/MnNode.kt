package io.peekandpoke.klang.sprudel.lang.parser

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern

/**
 * Intermediate AST node for mini-notation patterns.
 *
 * Used as the bridge between the raw string input and the [SprudelPattern] objects
 * the runtime executes. Storing this intermediate tree allows:
 *  - the visual editor to display and edit patterns without runtime dependencies
 *  - a clean round-trip: String → MnPattern → String
 *  - source-location tracking through to [AtomicPattern] nodes
 *
 * Every node carries a unique [id] (auto-generated, not part of structural equality).
 * The editor uses ids to locate and replace nodes in the tree.
 */
sealed class MnNode {

    /** Unique identity within a single tree — used for editor operations, not part of equals/hashCode. */
    abstract val id: Int

    companion object {
        private var idCounter = 0
        fun nextId(): Int = idCounter++
    }

    // ── Universal modifier bag ────────────────────────────────────────────────

    /**
     * Modifiers that can be attached to any node type.
     *
     * Phase 2 applies them in a fixed order: euclidean → multiplier → divisor
     * → probability → weight.  This order is consistent with the renderer,
     * guaranteeing round-trip stability.
     */
    data class Mods(
        val multiplier: Double? = null,  // *n  — play n times faster
        val divisor: Double? = null,  // /n  — play n times slower
        val weight: Double? = null,  // @n  — relative time weight in a sequence
        val probability: Double? = null,  // ?   or ?0.5 — random degradation probability
        val euclidean: Euclidean? = null, // (pulses, steps[, rotation])
    ) {
        val isEmpty: Boolean
            get() = multiplier == null && divisor == null && weight == null &&
                    probability == null && euclidean == null

        companion object {
            val None = Mods()
        }
    }

    data class Euclidean(val pulses: Int, val steps: Int, val rotation: Int = 0)

    // ── Concrete node types ───────────────────────────────────────────────────

    /** A single literal step (note, sound name, number, …). */
    data class Atom(
        val value: String,
        /** Character range in the original input string — for source location tracking. */
        val sourceRange: IntRange? = null,
        /**
         * 1-based line number within the mini-notation string.
         * Needed for multi-line strings to compute absolute source positions correctly.
         */
        val sourceLine: Int = 1,
        /**
         * 1-based column number within [sourceLine].
         * Defaults to `sourceRange?.first?.plus(1) ?: 1` when null (single-line shortcut).
         */
        val sourceColumn: Int? = null,
        /** The modifiers */
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()

        /** Creates a new atom with the given value, preserving modifiers. */
        fun withValue(newValue: String) = Atom(value = newValue, mods = mods)
    }

    /**
     * Bracketed sub-expression `[ … ]`.
     *
     * [items] is a flat sequence of nodes. Stacking inside a group is represented by a
     * [Stack] node as the single item: `Group(items = listOf(Stack(...)))`.
     */
    data class Group(
        val items: List<MnNode>,
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()

        fun insertAt(index: Int, node: MnNode) =
            Group(items = items.toMutableList().apply { add(index, node) }, mods = mods)
    }

    /** Cycle alternation `< a b c >` — plays items round-robin, one per cycle. */
    data class Alternation(
        val items: List<MnNode>,
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()

        fun insertAt(index: Int, node: MnNode) =
            Alternation(items = items.toMutableList().apply { add(index, node) }, mods = mods)
    }

    /**
     * Random choice `a | b | c` — picks one option per event.
     * Left-associative chains are flattened: `a | b | c` → Choice([a, b, c]).
     */
    data class Choice(
        val options: List<MnNode>,
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()
    }

    /**
     * Simultaneous stack of sequences `a b, c d` rendered as comma-separated layers.
     * When appearing inline within a sequence it is always bracketed: `[layer1, layer2]`.
     *
     * Unlike [Group], a Stack has no structural bracket of its own — the commas are the
     * only syntax that distinguishes it from a plain sequence.
     */
    data class Stack(
        val layers: List<List<MnNode>>,
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()

        fun addLayer(vararg nodes: MnNode) =
            Stack(layers = layers + listOf(nodes.toList()), mods = mods)
    }

    /**
     * Repeat `node!count` — deferred expansion of the bang operator.
     *
     * Keeps `a!8` as a single AST node so the renderer can produce `a!8` again
     * instead of `a a a a a a a a`. Phase 2 ([MnPatternToSprudelPattern]) expands it.
     *
     * [mods] are applied to the whole group of expanded copies (equivalent to wrapping
     * them in a [Group] with the same mods). When [mods] is empty the copies are
     * flattened directly into the enclosing sequence.
     */
    data class Repeat(
        val node: MnNode,
        val count: Int,
        val mods: Mods = Mods.None,
    ) : MnNode() {
        override val id: Int = nextId()
    }

    /** Silence `~`. Carries the source position so it can be located in the text for replacement. */
    data class Rest(val sourceRange: IntRange? = null, val mods: Mods = Mods.None) : MnNode() {
        override val id: Int = nextId()
    }

    /** Line break in a multi-line mini-notation string. Carries no musical meaning; used for visual layout. */
    class Linebreak : MnNode() {
        override val id: Int = nextId()
        override fun equals(other: Any?) = other is Linebreak
        override fun hashCode() = "Linebreak".hashCode()
        override fun toString() = "Linebreak"
    }

    // ── Modifier application helpers ─────────────────────────────────────────

    /** Returns a copy of this node with [transform] applied to its [Mods]. */
    fun withMod(transform: Mods.() -> Mods): MnNode = when (this) {
        is Atom -> copy(mods = mods.transform())
        is Group -> copy(mods = mods.transform())
        is Alternation -> copy(mods = mods.transform())
        is Choice -> copy(mods = mods.transform())
        is Stack -> copy(mods = mods.transform())
        is Repeat -> copy(mods = mods.transform())
        is Rest -> copy(mods = mods.transform())
        is Linebreak -> this
        is MnPattern -> this
    }

    fun modsOrNull(): Mods? = when (this) {
        is Atom -> mods
        is Group -> mods
        is Alternation -> mods
        is Choice -> mods
        is Stack -> mods
        is Repeat -> mods
        is Rest -> mods
        is Linebreak -> null
        is MnPattern -> null
    }

    // ── Tree navigation ─────────────────────────────────────────────────────

    /** Returns the direct children of this node as a flat list. */
    fun children(): List<MnNode> = when (this) {
        is MnPattern -> items
        is Group -> items
        is Alternation -> items
        is Choice -> options
        is Stack -> layers.flatten()
        is Repeat -> listOf(node)
        is Atom, is Rest, is Linebreak -> emptyList()
    }

    /** Depth-first traversal — calls [action] on this node, then recursively on all descendants. */
    fun walk(action: (MnNode) -> Unit) {
        action(this)
        children().forEach { it.walk(action) }
    }

    /** Depth-first search — returns the first non-null result of [match]. */
    fun <T> findFirst(match: (MnNode) -> T?): T? {
        match(this)?.let { return it }
        return children().firstNotNullOfOrNull { it.findFirst(match) }
    }

    /** Find a node by [targetId] anywhere in the tree. */
    fun findById(targetId: Int): MnNode? = findFirst { if (it.id == targetId) it else null }

    /** Replace a descendant (or self) by [targetId], returning a new tree. */
    fun replaceById(targetId: Int, replacement: MnNode): MnNode {
        if (id == targetId) return replacement
        return when (this) {
            is Atom, is Rest, is Linebreak -> this
            is MnPattern -> copy(items = items.map { it.replaceById(targetId, replacement) })
            is Group -> copy(items = items.map { it.replaceById(targetId, replacement) })
            is Alternation -> copy(items = items.map { it.replaceById(targetId, replacement) })
            is Choice -> copy(options = options.map { it.replaceById(targetId, replacement) })
            is Stack -> copy(layers = layers.map { layer -> layer.map { it.replaceById(targetId, replacement) } })
            is Repeat -> copy(node = node.replaceById(targetId, replacement))
        }
    }

    /** Remove a descendant by [targetId]. Returns null if this node itself was the target. */
    fun removeById(targetId: Int): MnNode? {
        if (id == targetId) return null
        return when (this) {
            is Atom, is Rest, is Linebreak -> this
            is MnPattern -> copy(items = items.mapNotNull { it.removeById(targetId) })
            is Group -> copy(items = items.mapNotNull { it.removeById(targetId) })
            is Alternation -> copy(items = items.mapNotNull { it.removeById(targetId) })
            is Choice -> copy(options = options.mapNotNull { it.removeById(targetId) })
            is Stack -> copy(layers = layers.map { layer -> layer.mapNotNull { it.removeById(targetId) } })
            is Repeat -> {
                val newNode = node.removeById(targetId)
                if (newNode == null) this else copy(node = newNode)
            }
        }
    }
}

// ── Top-level pattern ─────────────────────────────────────────────────────────

/**
 * The root of a parsed mini-notation string — a flat sequence of [MnNode]s.
 *
 * Extends [MnNode] so it participates in tree operations (replaceById, removeById, etc.).
 *
 * Stacking at the top level is represented by a single [MnNode.Stack] item:
 * `MnPattern(items = listOf(Stack(layers = [...])))`.
 */
data class MnPattern(val items: List<MnNode>) : MnNode() {

    override val id: Int = nextId()

    companion object {
        val Empty = MnPattern(items = emptyList())

        fun of(vararg nodes: MnNode) = MnPattern(items = nodes.toList())
    }

    fun insertAt(index: Int, node: MnNode) =
        MnPattern(items = items.toMutableList().apply { add(index, node) })

    /**
     * Splits the top-level item list on [MnNode.Linebreak] nodes.
     *
     * Each [MnNode.Linebreak] acts as a line separator. The result always contains
     * at least one entry; consecutive linebreaks produce empty [MnPattern]s.
     */
    fun splitOnLinebreaks(): List<MnPattern> {
        val result = mutableListOf<MnPattern>()
        var current = mutableListOf<MnNode>()
        for (item in items) {
            if (item is MnNode.Linebreak) {
                result.add(MnPattern(current))
                current = mutableListOf()
            } else {
                current.add(item)
            }
        }
        result.add(MnPattern(current))
        return result
    }
}
