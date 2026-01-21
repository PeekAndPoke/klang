package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that zooms into portions based on control patterns for start and end.
 *
 * For each pair of control events (start and end), zooms into that portion of the inner pattern.
 * Zoom works by shifting the pattern earlier and then speeding it up.
 *
 * @param inner The pattern to zoom into
 * @param startPattern The control pattern providing start positions (0.0 to 1.0)
 * @param endPattern The control pattern providing end positions (0.0 to 1.0)
 */
internal class ZoomPatternWithControl(
    val inner: StrudelPattern,
    val startPattern: StrudelPattern,
    val endPattern: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = inner.weight

    override val steps: Rational? get() = inner.steps

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
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
                val duration = end - start

                if (duration <= 0.0) continue

                // Calculate the overlap timespan
                val overlapBegin = maxOf(startEvent.begin, endEvent.begin)
                val overlapEnd = minOf(startEvent.end, endEvent.end)

                // Apply zoom: early(start).fast(duration)
                val zoomed =
                    TimeShiftPattern.static(source = inner, offset = start.toRational() * Rational.MINUS_ONE)

                val final =
                    TempoModifierPattern.static(zoomed, factor = duration.toRational(), invertPattern = true)

                val events: List<StrudelPatternEvent> =
                    final.queryArcContextual(overlapBegin, overlapEnd, ctx)

                result.addAll(events)
            }
        }

        return result
    }
}
