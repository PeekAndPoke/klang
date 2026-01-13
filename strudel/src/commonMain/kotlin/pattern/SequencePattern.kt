package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Sequence Pattern: Squashes multiple patterns into a single cycle.
 * Implementation of `seq(a, b)`.
 */
internal class SequencePattern(
    val patterns: List<StrudelPattern>,
) : StrudelPattern.FixedWeight {

    companion object {
        fun create(patterns: List<StrudelPattern>): StrudelPattern = when {
            patterns.isEmpty() -> EmptyPattern
            patterns.size == 1 -> patterns.first()
            else -> SequencePattern(patterns)
        }
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        // println("from $from, to $to")

        val events = mutableListOf<StrudelPatternEvent>()

        // Calculate proportional offsets based on weights
        val weights = patterns.map { it.weight }
        val totalWeight = weights.sum().toRational()
        val offsets = mutableListOf(Rational.ZERO)

        weights.forEach { w ->
            offsets.add(offsets.last() + (w.toRational() / totalWeight))
        }

        // Optimize: Iterate only over the cycles involved in the query
        val start = from
        val end = to

        val startCycle = start.floor().toInt()
        val endCycle = end.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            patterns.forEachIndexed { index, pattern ->
                val cycleOffset = Rational(cycle)
                val stepStart = cycleOffset + offsets[index]
                val stepEnd = cycleOffset + offsets[index + 1]
                val stepSize = offsets[index + 1] - offsets[index]

                // Calculate the intersection of this step with the query arc
                val intersectStart = maxOf(from, stepStart)
                val intersectEnd = minOf(to, stepEnd)

                // Condition for when to take the event
                val takeIt = intersectEnd > intersectStart

                if (takeIt) {
                    // Map the "outer" time to the "inner" pattern time.
                    // The inner pattern covers 0..1 logically for this step.
                    // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                    val innerFrom = (intersectStart - stepStart) / stepSize + cycleOffset
                    // Clamp innerTo to the end of the cycle
                    val innerTo = minOf((intersectEnd - stepStart) / stepSize + cycleOffset, cycleOffset + Rational.ONE)

                    val innerEvents = pattern.queryArcContextual(innerFrom, innerTo, ctx)

                    events.addAll(innerEvents.mapNotNull { ev ->
                        // Map back to outer time
                        val mappedBegin = (ev.begin - cycleOffset) * stepSize + stepStart
                        val mappedEnd = (ev.end - cycleOffset) * stepSize + stepStart
                        val mappedDur = mappedEnd - mappedBegin // Duration also scales

                        if (mappedEnd > from) {
                            ev.copy(begin = mappedBegin, end = mappedEnd, dur = mappedDur)
                        } else {
                            null
                        }
                    })
                }
            }
        }

        return events
    }
}
