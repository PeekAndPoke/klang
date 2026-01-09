package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Sequence Pattern: Squashes multiple patterns into a single cycle.
 * Implementation of `seq(a, b)`.
 */
internal class SequencePattern(
    val patterns: List<StrudelPattern>,
) : StrudelPattern.FixedWeight {

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()

        // Calculate proportional offsets based on weights
        val weights = patterns.map { it.weight }
        val totalWeight = weights.sum()
        val offsets = mutableListOf(Rational.ZERO)
        weights.forEach { w ->
            offsets.add(offsets.last() + (Rational(w) / Rational(totalWeight)))
        }

        // Optimize: Iterate only over the cycles involved in the query
        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        for (cycle in startCycle until endCycle) {
            patterns.forEachIndexed { index, pattern ->
                val cycleOffset = Rational(cycle)
                val stepStart = cycleOffset + offsets[index]
                val stepEnd = cycleOffset + offsets[index + 1]
                val stepSize = offsets[index + 1] - offsets[index]

                // Calculate the intersection of this step with the query arc
                val intersectStart = maxOf(from, stepStart)
                val intersectEnd = minOf(to, stepEnd)

                // If there is an overlap
                if (intersectEnd > intersectStart) {
                    // Map the "outer" time to the "inner" pattern time.
                    // The inner pattern covers 0..1 logically for this step.
                    // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                    val innerFrom = (intersectStart - stepStart) / stepSize + cycleOffset
                    // Clamp innerTo to the end of the cycle
                    val innerTo = minOf((intersectEnd - stepStart) / stepSize + cycleOffset, cycleOffset + Rational.ONE)

                    val innerEvents = pattern.queryArc(innerFrom, innerTo)

                    events.addAll(innerEvents.map { ev ->
                        // Map back to outer time
                        val mappedBegin = (ev.begin - cycleOffset) * stepSize + stepStart
                        val mappedEnd = (ev.end - cycleOffset) * stepSize + stepStart
                        val mappedDur = ev.dur * stepSize // Duration also scales

                        ev.copy(begin = mappedBegin, end = mappedEnd, dur = mappedDur)
                    })
                }
            }
        }

        return events
    }
}
