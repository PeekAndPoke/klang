package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * A pattern that slices the source into n parts and plays them according to indices,
 * where n can be controlled by a pattern.
 *
 * For each combination of n and index events, calculates slice boundaries and plays that slice.
 *
 * @param source The pattern to slice
 * @param nPattern The control pattern providing the number of slices
 * @param indices The pattern providing which slice indices to play
 */
internal class BitePatternWithControl(
    val source: StrudelPattern,
    val nPattern: StrudelPattern,
    val indices: StrudelPattern,
) : StrudelPattern {
    override val weight: Double get() = source.weight

    override val steps: Rational? get() = source.steps

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val nEvents = nPattern.queryArcContextual(from, to, ctx)
        val indexEvents = indices.queryArcContextual(from, to, ctx)

        if (nEvents.isEmpty() || indexEvents.isEmpty()) return emptyList()

        val result = mutableListOf<StrudelPatternEvent>()

        // Pair up n events with index events by their overlapping timespans
        for (nEvent in nEvents) {
            for (indexEvent in indexEvents) {
                // Check if events overlap
                if (nEvent.end <= indexEvent.begin || indexEvent.end <= nEvent.begin) continue

                val n = nEvent.data.value?.asInt ?: 4
                if (n <= 0) continue

                val idx = indexEvent.data.value?.asInt ?: 0

                // Handle wrapping like JS .mod(1)
                val normalizedIdx = idx.mod(n)
                val safeIdx = if (normalizedIdx < 0) normalizedIdx + n else normalizedIdx

                val start = safeIdx.toDouble() / n
                val end = (safeIdx + 1.0) / n

                // Calculate the overlap timespan
                val overlapBegin = maxOf(nEvent.begin, indexEvent.begin)
                val overlapEnd = minOf(nEvent.end, indexEvent.end)

                // Get the slice using zoom logic: early(start).fast(end - start)
                val duration = end - start
                if (duration <= 0.0) continue

                val slice = TempoModifierPattern.static(
                    source = TimeShiftPattern.static(
                        source = source,
                        offset = start.toRational() * Rational.MINUS_ONE,
                    ),
                    factor = duration.toRational(),
                    invertPattern = true,
                )

                val dur = (overlapEnd - overlapBegin).toDouble()
                if (dur <= 0.0) continue

                // Fit and position the slice
                val fitted = slice.fast(1.0 / dur).late(overlapBegin.toDouble())

                val events = fitted.queryArcContextual(overlapBegin, overlapEnd, ctx)
                result.addAll(events)
            }
        }

        return result
    }
}
