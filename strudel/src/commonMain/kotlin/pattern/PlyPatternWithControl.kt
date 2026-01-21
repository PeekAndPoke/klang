package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Repeats each event n times within its original timespan, where n is determined by a control pattern.
 *
 * @param source The source pattern whose events will be repeated
 * @param nPattern Pattern that determines the number of repetitions for each event
 */
internal class PlyPatternWithControl(
    val source: StrudelPattern,
    val nPattern: StrudelPattern,
) : StrudelPattern {

    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational {
        return source.estimateCycleDuration()
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val sourceEvents = source.queryArcContextual(from, to, ctx)
        val result = mutableListOf<StrudelPatternEvent>()

        for (event in sourceEvents) {
            // Query the control pattern at this event's timespan
            val controlEvents = nPattern.queryArcContextual(event.begin, event.end, ctx)

            if (controlEvents.isEmpty()) {
                // No control value, keep original event
                result.add(event)
                continue
            }

            // Get the n value from the first control event
            val nValue = controlEvents.first().data.value?.asInt ?: 1

            if (nValue <= 1) {
                // No repetition needed
                result.add(event)
                continue
            }

            val nRat = nValue.toRational()
            val eventDuration = event.end - event.begin
            val subDuration = eventDuration / nRat

            // Create n repetitions within the original event's timespan
            for (i in 0 until nValue) {
                val iRat = i.toRational()
                val newBegin = event.begin + (subDuration * iRat)
                val newEnd = newBegin + subDuration
                val newDur = subDuration

                // Only include if it intersects with our query range
                if (newEnd > from && newBegin < to) {
                    result.add(
                        event.copy(
                            begin = newBegin,
                            end = newEnd,
                            dur = newDur
                        )
                    )
                }
            }
        }

        return result
    }
}
