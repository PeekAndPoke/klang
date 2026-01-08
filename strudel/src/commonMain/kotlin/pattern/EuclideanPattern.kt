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

            rhythm.forEachIndexed { stepIndex, isActive ->
                if (isActive == 1) {
                    val stepStart = cycleOffset + (Rational(stepIndex) * stepDuration)
                    val stepEnd = stepStart + stepDuration

                    // Check intersection with query arc
                    val intersectStart = maxOf(from, stepStart)
                    val intersectEnd = minOf(to, stepEnd)

                    // No EPSILON needed - exact arithmetic!
                    if (intersectEnd > intersectStart) {
                        // Query the inner pattern at the EXACT same absolute time as the gate.
                        // This allows [a b...]/8 to play a, then b, then c as time passes naturally.
                        val innerFrom = intersectStart
                        val innerTo = intersectEnd

                        val innerEvents = inner.queryArc(innerFrom, innerTo)

                        events.addAll(innerEvents.map { ev ->
                            // Strict Clipping
                            // We constrain the event strictly to the current step window.
                            val clippedBegin = maxOf(ev.begin, stepStart)
                            val clippedEnd = minOf(ev.end, stepEnd)

                            val clippedDur = clippedEnd - clippedBegin

                            ev.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedDur
                            )
                        })
                    }
                }
            }
        }

        return events
    }
}
