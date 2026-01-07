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
internal class SequencePattern(val patterns: List<StrudelPattern>) : StrudelPattern {
    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        if (patterns.isEmpty()) return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()
        val stepSize = 1.0 / patterns.size

        // Optimize: Iterate only over the cycles involved in the query
        val startCycle = floor(from).toInt()
        val endCycle = ceil(to).toInt()

        for (cycle in startCycle until endCycle) {
            patterns.forEachIndexed { index, pattern ->
                val cycleOffset = cycle.toDouble()
                val stepStart = cycleOffset + (index * stepSize)
                val stepEnd = cycleOffset + ((index + 1) * stepSize)

                // Calculate the intersection of this step with the query arc
                val intersectStart = max(from, stepStart)
                val intersectEnd = min(to, stepEnd)

                // If there is an overlap
                if (intersectEnd > intersectStart) {
                    // Map the "outer" time to the "inner" pattern time.
                    // The inner pattern covers 0..1 logically for this step.
                    // Formula: t_inner = (t_outer - stepStart) / stepSize + cycle
                    val innerFrom = (intersectStart - stepStart) / stepSize + cycle
                    val innerTo = (intersectEnd - stepStart) / stepSize + cycle

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
