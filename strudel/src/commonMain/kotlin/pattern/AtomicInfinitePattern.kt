package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * An infinite pattern that repeats the given [data] every cycle (0..1, 1..2, ...).
 *
 * Equivalent to `pure(x)` in JS Strudel when used inside time transformations like `ply`.
 * Unlike [AtomicPattern] which usually represents a single event at 0..1, this pattern
 * conceptually exists across all time.
 */
class AtomicInfinitePattern(val data: StrudelVoiceData) : StrudelPattern {
    override val weight: Double = 1.0
    override val numSteps: Rational? = null // Infinite pattern has no defined step count

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        val result = mutableListOf<StrudelPatternEvent>()

        // Determine which cycles overlap with the query range
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (i in startCycle until endCycle) {
            val cycleStart = i.toRational()
            val cycleEnd = cycleStart + Rational.ONE

            // Check for overlap
            if (cycleStart < to && cycleEnd > from) {
                val timeSpan = io.peekandpoke.klang.strudel.TimeSpan(
                    begin = cycleStart,
                    end = cycleEnd
                )
                result.add(
                    StrudelPatternEvent(
                        part = timeSpan,
                        whole = timeSpan,
                        data = data
                    )
                )
            }
        }
        return result
    }
}
