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
        val pulseDuration = 1.0 / pulses.toDouble()

        for (cycle in startCycle until endCycle) {
            val cycleOffset = cycle.toDouble()
            var activePulseIndex = 0

            rhythm.forEachIndexed { stepIndex, isActive ->
                if (isActive == 1) {
                    val stepStart = cycleOffset + (stepIndex * stepDuration)
                    val stepEnd = stepStart + stepDuration

                    // Check intersection with query arc
                    val intersectStart = max(from, stepStart)
                    val intersectEnd = min(to, stepEnd)

                    if (intersectEnd > intersectStart) {
                        // Map the active step to the corresponding slice of the inner pattern
                        // preserving the cycle for long-running patterns.
                        // Inner pattern time = cycle + (pulseIndex / pulses)

                        val innerPulseStart = cycleOffset + (activePulseIndex * pulseDuration)

                        // We map the window [stepStart, stepEnd] to [innerPulseStart, innerPulseStart + pulseDuration]
                        val scale = pulseDuration / stepDuration

                        // Map query times
                        val innerFrom = innerPulseStart + (intersectStart - stepStart) * scale
                        val innerTo = innerPulseStart + (intersectEnd - stepStart) * scale

                        val innerEvents = inner.queryArc(innerFrom, innerTo)

                        events.addAll(innerEvents.map { ev ->
                            // Map back to outer time
                            val offsetFromPulseStart = ev.begin - innerPulseStart
                            val mappedBegin = stepStart + (offsetFromPulseStart / scale)
                            val mappedDur = ev.dur / scale
                            val mappedEnd = mappedBegin + mappedDur

                            // Strict clipping to the step window to ensure rhythm
                            val clippedBegin = max(mappedBegin, stepStart)
                            val clippedEnd = min(mappedEnd, stepEnd)
                            val clippedDur = max(0.0, clippedEnd - clippedBegin)

                            ev.copy(
                                begin = clippedBegin,
                                end = clippedEnd,
                                dur = clippedDur
                            )
                        }.filter { it.dur > 1e-9 })
                    }
                    activePulseIndex++
                }
            }
        }

        return events
    }
}
