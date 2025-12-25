package io.peekandpoke.klang.strudel

/**
 * Strudel pattern.
 */
interface StrudelPattern {
    /**
     * Queries events from [from] and [to] cycles.
     *
     * Also [sampleRate] is required for setting up oscillators and filters
     */
    fun queryArc(from: Double, to: Double, sampleRate: Int): List<StrudelPatternEvent>
}
