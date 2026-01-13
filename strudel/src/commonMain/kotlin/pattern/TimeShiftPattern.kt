package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Shifts a pattern in time by a given offset.
 * Positive offset shifts the pattern later (to the right).
 * Negative offset shifts the pattern earlier (to the left).
 */
internal class TimeShiftPattern(
    val source: StrudelPattern,
    val offset: Rational,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // To shift the pattern by 'offset', we query the source at (from - offset, to - offset)
        // and then shift all resulting events forward by 'offset'
        val innerFrom = from - offset
        val innerTo = to - offset

        val innerEvents = source.queryArcContextual(innerFrom, innerTo, ctx)

        return innerEvents.mapNotNull { ev ->
            val mappedBegin = ev.begin + offset
            val mappedEnd = ev.end + offset

            // Only include events that overlap with the requested range
            if (mappedEnd > from && mappedBegin <= to) {
                ev.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                )
            } else {
                null
            }
        }
    }
}

/**
 * Shifts a pattern in time by an offset specified by another pattern.
 * The offset pattern is sampled at the beginning of each event.
 */
internal class TimeShiftPatternWithControl(
    val source: StrudelPattern,
    val offsetPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        // We need to query a wider range from the source to account for potential shifts
        // Use a heuristic: query +/- 10 cycles extra to be safe against larger control values
        val margin = Rational(10)
        val extendedFrom = from - margin
        val extendedTo = to + margin

        val sourceEvents = source.queryArcContextual(extendedFrom, extendedTo, ctx)
        if (sourceEvents.isEmpty()) return emptyList()

        val epsilon = 1e-5.toRational()

        return sourceEvents.mapNotNull { event ->
            val queryTime = event.begin

            // Sample the offset pattern at the event's begin time
            val offsetEvents = offsetPattern.queryArcContextual(queryTime, queryTime + epsilon, ctx)
            val offsetValue = offsetEvents.firstOrNull()?.data?.value?.asDouble ?: 0.0
            val offset = offsetValue.toRational()

//            println("Note: ${event.data.note} | Offset: $offset")

            // Shift the event by the sampled offset
            val mappedBegin = event.begin + offset
            val mappedEnd = event.end + offset

            // Only include events that overlap with the requested range
            if (mappedEnd > from && mappedBegin < to) {
                event.copy(
                    begin = mappedBegin,
                    end = mappedEnd,
                )
            } else {
                null
            }
        }
    }
}
