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
 *
 * This pattern acts as a gate (`struct`). It only allows the inner pattern to play
 * during the active steps of the Euclidean rhythm.
 * Events are strictly clipped to the step duration to ensure the rhythmic structure is preserved.
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
                        // Query the inner pattern at the absolute time of the step.
                        // This allows the inner pattern to progress naturally (e.g. if it's slow).
                        val innerEvents = inner.queryArc(intersectStart, intersectEnd)

                        events.addAll(innerEvents.mapNotNull { ev ->
                            // Strictly clip the event to the step window
                            val clippedBegin = max(ev.begin, stepStart)
                            val clippedEnd = min(ev.end, stepEnd)
                            val clippedDur = clippedEnd - clippedBegin

                            if (clippedDur > 1e-9) {
                                ev.copy(
                                    begin = clippedBegin,
                                    end = clippedEnd,
                                    dur = clippedDur
                                )
                            } else {
                                null
                            }
                        })
                    }
                }
            }
        }

        return events
    }
}
