package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

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

