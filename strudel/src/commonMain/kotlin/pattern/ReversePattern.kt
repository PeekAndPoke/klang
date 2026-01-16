package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Reverses the pattern within each cycle.
 */
internal class ReversePattern(val inner: StrudelPattern) : StrudelPattern {
    override val weight: Double get() = inner.weight
    override val steps: Rational? get() = inner.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val startCycle = from.floor()
        val endCycle = to.ceil()
        val events = mutableListOf<StrudelPatternEvent>()

        for (c in startCycle.toLong()..endCycle.toLong()) {
            val cycle = c.toRational()
            val nextCycle = cycle + Rational.ONE

            // Intersect query with current cycle
            val intersectStart = maxOf(from, cycle)
            val intersectEnd = minOf(to, nextCycle)

            if (intersectEnd > intersectStart) {
                // Map the intersected range to reversed inner time
                val innerTo = Rational.ONE + (cycle * Rational(2)) - intersectStart
                val innerFrom = Rational.ONE + (cycle * Rational(2)) - intersectEnd

                inner.queryArcContextual(innerFrom, innerTo, ctx).forEach { ev ->
                    val mappedBegin = Rational.ONE + (cycle * Rational(2)) - ev.end
                    val mappedEnd = Rational.ONE + (cycle * Rational(2)) - ev.begin
                    events.add(ev.copy(begin = mappedBegin, end = mappedEnd))
                }
            }
        }

        return events
    }
}
