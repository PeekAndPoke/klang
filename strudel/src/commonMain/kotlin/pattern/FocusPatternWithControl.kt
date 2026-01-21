package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that focuses based on control patterns for start and end.
 *
 * For each pair of control events (start and end), applies the focus transformation.
 *
 * @param source The pattern to focus
 * @param startPattern The control pattern providing start positions (0.0 to 1.0)
 * @param endPattern The control pattern providing end positions (0.0 to 1.0)
 */
internal class FocusPatternWithControl(
    val source: StrudelPattern,
    val startPattern: StrudelPattern,
    val endPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        val startEvents = startPattern.queryArcContextual(from, to, ctx)
        val endEvents = endPattern.queryArcContextual(from, to, ctx)

        if (startEvents.isEmpty() || endEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // Pair up start and end events by their timespans
        for (startEvent in startEvents) {
            for (endEvent in endEvents) {
                // Check if events overlap
                if (startEvent.end <= endEvent.begin || endEvent.end <= startEvent.begin) continue

                val start = startEvent.data.value?.asDouble ?: 0.0
                val end = endEvent.data.value?.asDouble ?: 1.0

                if (start >= end) continue

                // Calculate the overlap timespan
                val overlapBegin = maxOf(startEvent.begin, endEvent.begin)
                val overlapEnd = minOf(startEvent.end, endEvent.end)

                // Apply focus transformation: early(start.floor()) -> fast(1/span) -> late(start)
                // start.floor() gives the cycle start (sam in Strudel terms)
                val span = end - start
                val factor = 1.0 / span
                val startRat = start.toRational()

                val step1 = TimeShiftPattern(source, -startRat.floor())
                val step2 = TempoModifierPattern(source = step1, factor = factor.toRational(), invertPattern = true)
                val focused = TimeShiftPattern(source = step2, offset = startRat)

                val events: List<StrudelPatternEvent> =
                    focused.queryArcContextual(overlapBegin, overlapEnd, ctx)

                result.addAll(events)
            }
        }

        return result
    }
}
