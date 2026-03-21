package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.TimeSpan

/**
 * An infinite pattern that repeats the given [data] every cycle (0..1, 1..2, ...).
 *
 * Equivalent to `pure(x)` in JS Strudel when used inside time transformations like `ply`.
 * Unlike [AtomicPattern] which usually represents a single event at 0..1, this pattern
 * conceptually exists across all time.
 */
class AtomicInfinitePattern(val data: SprudelVoiceData) : SprudelPattern {
    override val weight: Double = 1.0
    override val numSteps: Rational? = null // Infinite pattern has no defined step count

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        val result = mutableListOf<SprudelPatternEvent>()

        // Determine which cycles overlap with the query range
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (i in startCycle until endCycle) {
            val cycleStart = i.toRational()
            val cycleEnd = cycleStart + Rational.ONE

            // Check for overlap
            if (cycleStart < to && cycleEnd > from) {
                val timeSpan = TimeSpan(begin = cycleStart, end = cycleEnd)

                result.add(
                    SprudelPatternEvent(part = timeSpan, whole = timeSpan, data = data)
                )
            }
        }
        return result
    }
}
