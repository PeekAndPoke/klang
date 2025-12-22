package io.peekandpoke

import kotlinx.coroutines.Deferred

/**
 * Strudel pattern compiler.
 */
interface StrudelCompiler {
    fun compile(pattern: String): Deferred<StrudelPattern>
}

/**
 * Strudel pattern.
 */
interface StrudelPattern {
    /**
     * Queries events from [from] and [to] cycles.
     *
     * Also [sampleRate] is required for setting up oscillators and filters
     */
    fun queryArc(from: Double, to: Double, sampleRate: Int): List<StrudelEvent>
}
