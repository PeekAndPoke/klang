package io.peekandpoke

import kotlinx.coroutines.Deferred
import kotlin.math.PI

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

/**
 * Common numbers for dsp etc.
 */
object Numbers {
    const val TWO_PI: Double = PI * 2
}
