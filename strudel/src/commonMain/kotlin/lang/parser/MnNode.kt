package io.peekandpoke.klang.strudel.lang.parser

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
        val mods: Mods = Mods.None,
    ) : MnNode()

    /**
     * Bracketed sub-expression `[ … ]`.
     *
     * [layers] mirrors the top-level structure: usually one layer (simple group),
     * but comma-separated content inside brackets creates a stack within the group.
     */
    data class Group(
        val layers: List<List<MnNode>>,
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

    /** Silence `~`. */
    object Rest : MnNode()

    // ── Modifier application helpers ─────────────────────────────────────────

    /** Returns a copy of this node with [transform] applied to its [Mods]. */
    fun withMod(transform: Mods.() -> Mods): MnNode = when (this) {
        is Atom -> copy(mods = mods.transform())
        is Group -> copy(mods = mods.transform())
        is Alternation -> copy(mods = mods.transform())
        is Choice -> copy(mods = mods.transform())
        is Rest -> this  // Rest has no modifiers (always silent)
    }

    fun modsOrNull(): Mods? = when (this) {
        is Atom -> mods
        is Group -> mods
        is Alternation -> mods
        is Choice -> mods
        is Rest -> null
    }
}

// ── Top-level pattern ─────────────────────────────────────────────────────────

/**
 * The root of a parsed mini-notation string.
 *
 * [layers] contains a single entry for a plain sequence, or multiple entries when
 * the top-level uses comma-separated stacking (`a b, c d`).
 */
data class MnPattern(val layers: List<List<MnNode>>) {

    companion object {
        val Empty = MnPattern(layers = emptyList())

        /** Convenience constructor for a single-layer pattern. */
        fun of(vararg items: MnNode) = MnPattern(layers = listOf(items.toList()))
    }

    /** True when this pattern is a simultaneous stack (comma-separated layers). */
    val isStack: Boolean get() = layers.size > 1

    /** The first (and usually only) layer — convenience for single-layer patterns. */
    val items: List<MnNode> get() = layers.firstOrNull() ?: emptyList()
}
