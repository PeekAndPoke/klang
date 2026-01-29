package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Conditionally applies a transformation based on a pattern.
 *
 * Equivalent to JavaScript: pat.when(condition, func)
 *
 * For each event in the source pattern:
 * - Samples the condition pattern at the event's midpoint
 * - If the condition is truthy, applies the transformation
 * - Otherwise, keeps the event unchanged
 */
internal class WhenPattern(
    val source: StrudelPattern,
    val condition: StrudelPattern,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {

    override val weight: Double = source.weight
    override val numSteps: Rational? = source.numSteps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val transformedSource = transform(source)
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = createEventList()
        val epsilon = 1e-6.toRational()

        for (sourceEvent in sourceEvents) {
            val mid = (sourceEvent.begin + sourceEvent.end) / Rational(2)

            // Sample condition at midpoint
            val conditionEvents = condition.queryArcContextual(mid, mid + epsilon, ctx)
            val isTrue = conditionEvents.any { it.data.isTruthy() }

            if (isTrue) {
                // Query transformed pattern for ALL events in this source event's timespan
                val transformedEvents = transformedSource.queryArcContextual(
                    sourceEvent.begin,
                    sourceEvent.end,
                    ctx
                )
                if (transformedEvents.isNotEmpty()) {
                    result.addAll(transformedEvents)
                } else {
                    // Fallback: use source event if no transformed events found
                    result.add(sourceEvent)
                }
            } else {
                // Use original event
                result.add(sourceEvent)
            }
        }

        return result
    }

    private fun StrudelVoiceData.isTruthy(): Boolean {
        val noteStr = note ?: ""
        val valueTruthy = value?.isTruthy() ?: false
        val noteTruthy = noteStr.isNotEmpty() && noteStr != "~" && noteStr != "0" && noteStr != "false"
        return valueTruthy || noteTruthy
    }
}
