package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Euclidean Morph Pattern with control patterns for pulses, steps, and groove.
 *
 * For each combination of overlapping control events, applies the euclidish rhythm
 * with the corresponding parameter values.
 *
 * @param pulsesPattern Control pattern for number of pulses
 * @param stepsPattern Control pattern for number of steps
 * @param groovePattern Control pattern for groove/morph factor (0=euclidean, 1=even)
 */
internal class EuclideanMorphPatternWithControl(
    val pulsesPattern: StrudelPattern,
    val stepsPattern: StrudelPattern,
    val groovePattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = groovePattern.weight

    override val steps: Rational? get() = stepsPattern.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val pulsesEvents = pulsesPattern.queryArcContextual(from, to, ctx)
        val stepsEvents = stepsPattern.queryArcContextual(from, to, ctx)
        val grooveEvents = groovePattern.queryArcContextual(from, to, ctx)

        if (pulsesEvents.isEmpty() || stepsEvents.isEmpty() || grooveEvents.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<StrudelPatternEvent>()

        // Find all overlapping combinations of pulses, steps, and groove events
        for (pulsesEvent in pulsesEvents) {
            for (stepsEvent in stepsEvents) {
                for (grooveEvent in grooveEvents) {
                    // Check if these three events overlap
                    val overlapBegin = maxOf(
                        pulsesEvent.begin,
                        stepsEvent.begin,
                        grooveEvent.begin
                    )
                    val overlapEnd = minOf(
                        pulsesEvent.end,
                        stepsEvent.end,
                        grooveEvent.end
                    )

                    if (overlapEnd <= overlapBegin) continue

                    val pulses = pulsesEvent.data.value?.asInt ?: 0
                    val steps = stepsEvent.data.value?.asInt ?: 0

                    if (pulses <= 0 || steps <= 0) continue

                    // Create a sub-groove pattern that only covers this overlap
                    val subGroovePattern = object : StrudelPattern {
                        override val weight = groovePattern.weight
                        override val steps: Rational = Rational.ONE

                        override fun queryArcContextual(
                            from: Rational,
                            to: Rational,
                            ctx: QueryContext,
                        ): List<StrudelPatternEvent> {
                            // Only return the groove event for this specific overlap
                            return if (from < overlapEnd && to > overlapBegin) {
                                listOf(
                                    grooveEvent.copy(
                                        begin = maxOf(from, grooveEvent.begin),
                                        end = minOf(to, grooveEvent.end)
                                    ).let { it.copy(dur = it.end - it.begin) })
                            } else {
                                emptyList()
                            }
                        }
                    }

                    // Apply the EuclideanMorphPattern for this overlap timespan
                    val morphPattern = EuclideanMorphPattern(pulses, steps, subGroovePattern)
                    val events = morphPattern.queryArcContextual(overlapBegin, overlapEnd, ctx)
                    result.addAll(events)
                }
            }
        }

        return result
    }
}
