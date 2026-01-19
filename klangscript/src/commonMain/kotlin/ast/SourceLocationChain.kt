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
 * @property locations List of locations from outermost (call site) to innermost (atom)
 */
data class SourceLocationChain(
    val locations: List<SourceLocation>,
) {
    companion object {
        /**
         * Create a chain from a single location
         */
        fun single(location: SourceLocation): SourceLocationChain =
            SourceLocationChain(listOf(location))

        /**
         * Create an empty chain (no location information)
         */
        val empty: SourceLocationChain = SourceLocationChain(emptyList())
    }

    /**
     * Add a location to the end of the chain (innermost position)
     */
    fun append(location: SourceLocation): SourceLocationChain =
        SourceLocationChain(locations + location)

    /**
     * Add a location to the beginning of the chain (outermost position)
     */
    fun prepend(location: SourceLocation): SourceLocationChain =
        SourceLocationChain(listOf(location) + locations)

    /**
     * Combine two chains
     */
    fun plus(other: SourceLocationChain): SourceLocationChain =
        SourceLocationChain(locations + other.locations)

    /**
     * Get the outermost (call site) location
     */
    val outermost: SourceLocation? get() = locations.firstOrNull()

    /**
     * Get the innermost (most specific) location
     */
    val innermost: SourceLocation? get() = locations.lastOrNull()

    /**
     * Check if the chain is empty
     */
    val isEmpty: Boolean get() = locations.isEmpty()

    /**
     * Check if the chain has any locations
     */
    val isNotEmpty: Boolean get() = locations.isNotEmpty()

    override fun toString(): String {
        if (isEmpty) return "SourceLocationChain(empty)"
        return "SourceLocationChain(${locations.joinToString(" -> ")})"
    }
}
