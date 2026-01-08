package io.peekandpoke.klang.strudel.patterns

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Sequence Pattern: Squashes multiple patterns into a single cycle.
 * Implementation of `seq(a, b)`.
 */
internal class SequencePattern(
    val patterns: List<StrudelPattern>,
) : StrudelPattern.Fixed {

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()

        // Calculate proportional offsets based on weights
        val weights = patterns.map { it.weight }
        val totalWeight = weights.sum()
        val offsets = mutableListOf(0.0)
        weights.forEach { w ->
            offsets.add(offsets.last() + (w / totalWeight))
        }

        // Optimize: Iterate only over the cycles involved in the query
        val startCycle = floor(from).toInt()
        val endCycle = ceil(to).toInt()

        for (cycle in startCycle until endCycle) {
            patterns.forEachIndexed { index, pattern ->
                val cycleOffset = cycle.toDouble()
                val stepStart = cycleOffset + offsets[index]
                val stepEnd = cycleOffset + offsets[index + 1]
                val stepSize = offsets[index + 1] - offsets[index]

                // Calculate the intersection of this step with the query arc
                val intersectStart = max(from, stepStart)
                val intersectEnd = min(to, stepEnd)

                // If there is an overlap
                if (intersectEnd > intersectStart) {
                    // Map the "outer" time to the "inner" pattern time.
                    // The inner pattern covers 0..1 logically for this step.
                    // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                    val innerFrom = (intersectStart - stepStart) / stepSize + cycle
                    // Clamp innerTo to the end of the cycle to avoid floating point errors
                    // picking up events from the next cycle of the inner pattern.
                    val innerTo = min((intersectEnd - stepStart) / stepSize + cycle, cycle + 1.0)

                    val innerEvents = pattern.queryArc(innerFrom, innerTo)

                    events.addAll(innerEvents.map { ev ->
                        // Map back to outer time
                        val mappedBegin = (ev.begin - cycle) * stepSize + stepStart
                        val mappedEnd = (ev.end - cycle) * stepSize + stepStart
                        val mappedDur = ev.dur * stepSize // Duration also scales

                        ev.copy(begin = mappedBegin, end = mappedEnd, dur = mappedDur)
                    })
                }
            }
        }

        return events
    }
}
