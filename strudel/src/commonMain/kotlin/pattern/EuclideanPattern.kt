package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.tones.time.Rhythm
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Euclidean Pattern: Applies a Euclidean rhythm to an inner pattern.
 * Syntax: `pattern(pulses, steps)` e.g., `bd(3,8)`
 */
internal class EuclideanPattern(
    val inner: StrudelPattern,
    val pulses: Int,
    val steps: Int,
) : StrudelPattern {

    override val weight: Double get() = inner.weight

    private val rhythm = Rhythm.euclid(steps, pulses)

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        val events = mutableListOf<StrudelPatternEvent>()

        val startCycle = floor(from).toInt()
        val endCycle = ceil(to).toInt()

        val stepDuration = 1.0 / steps.toDouble()

        for (cycle in startCycle until endCycle) {
            val cycleOffset = cycle.toDouble()

            rhythm.forEachIndexed { stepIndex, isActive ->
                if (isActive == 1) {
                    val stepStart = cycleOffset + (stepIndex * stepDuration)
                    val stepEnd = stepStart + stepDuration

                    // Check intersection with query arc
                    val intersectStart = max(from, stepStart)
                    val intersectEnd = min(to, stepEnd)

                    if (intersectEnd > intersectStart) {
                        // Map outer time to inner time (0..1 for the inner pattern)
                        // Formula: t_inner = (t_outer - stepStart) / stepDuration

                        // FIX: Add 'cycle' to inner query so slowly evolving patterns (like those using /8)
                        // progress correctly over time instead of resetting to cycle 0 every step.
                        // Standard patterns repeat every cycle, so querying 1.0..2.0 is same as 0.0..1.0.
                        val innerFrom = cycle + (intersectStart - stepStart) / stepDuration

                        // Clamp innerTo to end of this logical cycle (cycle + 1.0)
                        // The inner pattern is squeezed into the step, so 1 full inner cycle = 1 step.
                        val innerTo = cycle + min((intersectEnd - stepStart) / stepDuration, 1.0)

                        val innerEvents = inner.queryArc(innerFrom, innerTo)

                        events.addAll(innerEvents.map { ev ->
                            // Map back to outer time
                            // We need to subtract 'cycle' before scaling back down
                            val relativeInnerBegin = ev.begin - cycle
                            val relativeInnerEnd = ev.end - cycle

                            val mappedBegin = (relativeInnerBegin * stepDuration) + stepStart
                            val mappedEnd = (relativeInnerEnd * stepDuration) + stepStart
                            val mappedDur = ev.dur * stepDuration

                            ev.copy(begin = mappedBegin, end = mappedEnd, dur = mappedDur)
                        })
                    }
                }
            }
        }

        return events
    }
}
