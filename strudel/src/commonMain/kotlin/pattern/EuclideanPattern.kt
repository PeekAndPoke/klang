package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.tones.time.Rhythm

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
    val rotation: Int,
) : StrudelPattern {

    override val weight: Double get() = inner.weight

    private val rhythm = Rhythm.euclid(steps, pulses)

    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        val events = mutableListOf<StrudelPatternEvent>()

        val startCycle = from.floor().toInt()
        val endCycle = to.ceil().toInt()

        // EXACT step duration using rational arithmetic - no floating point drift!
        val stepDuration = Rational.ONE / Rational(steps)

        for (cycle in startCycle until endCycle) {
            val cycleOffset = Rational(cycle)

            rhythm.forEachIndexed { rawIndex, isActive ->
                if (isActive == 1) {
                    // Apply rotation: shift the starting position of the pulses.
                    // (rawIndex - rotation) ensures that a positive rotation shifts pulses to the left.
                    val stepIndex = ((rawIndex - rotation) % steps + steps) % steps
                    val stepStart = cycleOffset + (Rational(stepIndex) * stepDuration)
                    val stepEnd = stepStart + stepDuration

                    // Check intersection with query arc
                    val intersectStart = maxOf(from, stepStart)
                    val intersectEnd = minOf(to, stepEnd)

                    // No EPSILON needed - exact arithmetic!
                    if (intersectEnd > intersectStart) {
                        // We only take the latest event to avoid "snapping" from one note to the next
                        val innerEvents = inner.queryArc(intersectStart, intersectEnd)
                            .sortedBy { it.begin }
                            .take(1)

                        events.addAll(innerEvents.map { ev ->
                            ev.copy(
                                begin = intersectStart,
                                end = intersectEnd,
                                dur = intersectEnd - intersectStart,
                            )
                        })
                    }
                }
            }
        }

        return events
    }
}
