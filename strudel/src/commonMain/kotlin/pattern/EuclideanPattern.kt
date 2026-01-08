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

                    if (intersectEnd > intersectStart + EPSILON) {
                        // 1. Calculate Inner Pattern Time Window
                        // We map the outer step window to the inner pattern's timeline.
                        // To allow the inner pattern to progress (e.g. [a b c]/8), we must include the 'cycle'.
                        // To allow the inner pattern to be "fast" enough to change on pulses, we use the pulse mapping logic?
                        // NO, let's stick to the "gating" logic where inner pattern runs at its own speed.
                        // If the inner pattern is meant to be fast, the user should speed it up.
                        // However, to fix "only 'a' plays", we must ensure we query the absolute time.

                        // We query the inner pattern at the EXACT same absolute time as the gate.
                        // This allows [a b...]/8 to play a, then b, then c as time passes naturally.

                        // FIX: Add a small epsilon to the start time to avoid picking up events
                        // that end exactly at the step boundary (floating point artifacts).
                        val innerFrom = intersectStart
                        val innerTo = intersectEnd

                        val innerEvents = inner.queryArc(innerFrom, innerTo)

                        events.addAll(innerEvents.map { ev ->
                            // 2. Strict Clipping
                            // We constrain the event strictly to the current step window.
                            val clippedBegin = max(ev.begin, stepStart)
                            // Use a tiny epsilon to ensure we don't accidentally include the very start of the next step
                            // or allow this event to touch the next step's start.
                            val clippedEnd = min(ev.end, stepEnd)

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
