package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.AtomicPattern

/**
 * Intermediate AST node for mini-notation patterns.
 *
 * Used as the bridge between the raw string input and the [StrudelPattern] objects
 * the runtime executes. Storing this intermediate tree allows:
 *  - the visual editor to display and edit patterns without runtime dependencies
 *  - a clean round-trip: String → MnPattern → String
 *  - source-location tracking through to [AtomicPattern] nodes
 */
sealed class MnNode {
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
        /**
         * Stable identity key within a single parse — equals [sourceRange]`.first`, or -1 when
         * no source location is available (e.g. programmatically constructed atoms).
         *
         * Two atoms in the same [MnPattern] always have distinct ids (≥ 0).
         * Use this instead of reference equality (`===`) or value-string matching when you
         * need to re-find an atom in a re-parsed tree after a text edit.
         */
        val id: Int get() = sourceRange?.first ?: -1
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
    ) : MnNode()

    /** Cycle alternation `< a b c >` — plays items round-robin, one per cycle. */
    data class Alternation(
        val items: List<MnNode>,
        val mods: Mods = Mods.None,
    ) : MnNode()

    /**
     * Random choice `a | b | c` — picks one option per event.
     * Left-associative chains are flattened: `a | b | c` → Choice([a, b, c]).
     */
    data class Choice(
        val options: List<MnNode>,
        val mods: Mods = Mods.None,
    ) : MnNode()

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
    ) : MnNode()

    /**
     * Repeat `node!count` — deferred expansion of the bang operator.
     *
     * Keeps `a!8` as a single AST node so the renderer can produce `a!8` again
     * instead of `a a a a a a a a`. Phase 2 ([MnPatternToStrudelPattern]) expands it.
     *
     * [mods] are applied to the whole group of expanded copies (equivalent to wrapping
     * them in a [Group] with the same mods). When [mods] is empty the copies are
     * flattened directly into the enclosing sequence.
     */
    data class Repeat(
        val node: MnNode,
        val count: Int,
        val mods: Mods = Mods.None,
    ) : MnNode()

    /** Silence `~`. */
    object Rest : MnNode()

    /** Line break in a multi-line mini-notation string. Carries no musical meaning; used for visual layout. */
    object Linebreak : MnNode()

    // ── Modifier application helpers ─────────────────────────────────────────

    /** Returns a copy of this node with [transform] applied to its [Mods]. */
    fun withMod(transform: Mods.() -> Mods): MnNode = when (this) {
        is Atom -> copy(mods = mods.transform())
        is Group -> copy(mods = mods.transform())
        is Alternation -> copy(mods = mods.transform())
        is Choice -> copy(mods = mods.transform())
        is Stack -> copy(mods = mods.transform())
        is Repeat -> copy(mods = mods.transform())
        is Rest -> this
        is Linebreak -> this
    }

    fun modsOrNull(): Mods? = when (this) {
        is Atom -> mods
        is Group -> mods
        is Alternation -> mods
        is Choice -> mods
        is Stack -> mods
        is Repeat -> mods
        is Rest -> null
        is Linebreak -> null
    }
}

// ── Top-level pattern ─────────────────────────────────────────────────────────

/**
 * The root of a parsed mini-notation string — a flat sequence of [MnNode]s.
 *
 * Stacking at the top level is represented by a single [MnNode.Stack] item:
 * `MnPattern(items = listOf(Stack(layers = [...])))`.
 */
data class MnPattern(val items: List<MnNode>) {

    companion object {
        val Empty = MnPattern(items = emptyList())

        fun of(vararg nodes: MnNode) = MnPattern(items = nodes.toList())
    }

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
