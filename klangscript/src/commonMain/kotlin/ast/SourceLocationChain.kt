package io.peekandpoke.klang.script.ast

/**
 * A chain of source locations tracking transformation path
 *
 * Used for live code highlighting to track how a value propagates from:
 * - Call site (e.g., `sound("bd hh sd")`)
 * - String literal location (e.g., `"bd hh sd"`)
 * - Individual token locations (e.g., `bd`, `hh`, `sd`)
 *
 * Example:
 * ```
 * sound("bd hh sd hh")
 *       ^-- String location
 *       ^bd ^hh ^sd ^hh -- Individual atom locations
 * ```
 *
 * The chain allows the frontend to highlight:
 * 1. The entire function call
 * 2. The specific string parameter
 * 3. The specific atom being played
 *
 * Backed by an Array for minimal allocation on prepend/append (the hot path).
 */
class SourceLocationChain private constructor(
    private val items: Array<SourceLocation>,
) {
    companion object {
        private val emptyArray = emptyArray<SourceLocation>()

        fun single(location: SourceLocation): SourceLocationChain =
            SourceLocationChain(arrayOf(location))

        val empty: SourceLocationChain = SourceLocationChain(emptyArray)

        fun of(locations: List<SourceLocation>): SourceLocationChain =
            if (locations.isEmpty()) empty else SourceLocationChain(locations.toTypedArray())

        private fun Array<SourceLocation>.concat(other: Array<SourceLocation>): Array<SourceLocation> =
            Array(size + other.size) { i -> if (i < size) this[i] else other[i - size] }

        private fun Array<SourceLocation>.concat(other: List<SourceLocation>): Array<SourceLocation> =
            Array(size + other.size) { i -> if (i < size) this[i] else other[i - size] }

        private fun List<SourceLocation>.concat(other: Array<SourceLocation>): Array<SourceLocation> =
            Array(size + other.size) { i -> if (i < size) this[i] else other[i - size] }
    }

    val locations: List<SourceLocation> get() = items.asList()

    /** Add a location to the end of the chain (innermost position) */
    fun append(location: SourceLocation): SourceLocationChain =
        SourceLocationChain(items.concat(arrayOf(location)))

    /** Add a list of locations to the end of the chain */
    fun append(locations: List<SourceLocation>): SourceLocationChain {
        if (locations.isEmpty()) return this
        return SourceLocationChain(items.concat(locations))
    }

    /** Add a location to the beginning of the chain (outermost position) */
    fun prepend(location: SourceLocation): SourceLocationChain =
        SourceLocationChain(arrayOf(location).concat(items))

    /** Add a list of locations to the beginning of the chain */
    fun prepend(locations: List<SourceLocation>): SourceLocationChain {
        if (locations.isEmpty()) return this
        return SourceLocationChain(locations.concat(items))
    }

    /** Combine two chains */
    fun plus(other: SourceLocationChain): SourceLocationChain {
        if (other.items.isEmpty()) return this
        if (items.isEmpty()) return other
        return SourceLocationChain(items.concat(other.items))
    }

    /** Get the outermost (call site) location */
    val outermost: SourceLocation? get() = items.firstOrNull()

    /** Get the innermost (most specific) location */
    val innermost: SourceLocation? get() = items.lastOrNull()

    val isEmpty: Boolean get() = items.isEmpty()

    val isNotEmpty: Boolean get() = items.isNotEmpty()

    override fun toString(): String {
        if (isEmpty) return "SourceLocationChain(empty)"
        return "SourceLocationChain(${items.joinToString(" -> ")})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourceLocationChain) return false
        return items.contentEquals(other.items)
    }

    override fun hashCode(): Int = items.contentHashCode()
}
